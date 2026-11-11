import { query } from '../db/pool.js';
import { enrichReading } from '../domain/derive.js';
import { evaluateAlerts } from '../domain/fusionAlertEngine.js';
import { computeHealthScore } from '../domain/healthScore.js';
import type { SensorIngestInput } from '../types.js';
import { getOrCreateBaseline, refreshBaseline } from './baselineService.js';

export async function ingestReading(input: SensorIngestInput) {
  const r = enrichReading(input);
  const baseline = await getOrCreateBaseline(r.petId);

  const inserted = await query<{ id: string }>(
    `INSERT INTO sensor_readings (
      pet_id, recorded_at, accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z,
      enclosure_temp_c, accel_energy, gyro_energy, jerk, heart_rate_bpm,
      hrv_rmssd_ms, signal_quality, resting_heart_rate, artifact_rejected,
      activity, movement_count, laying_minutes, standing_minutes,
      sleeping_minutes, active_minutes
    ) VALUES (
      $1, COALESCE($2::timestamptz, NOW()), $3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,
      $14,$15,$16,$17,$18,$19,$20,$21,$22,$23
    ) RETURNING id`,
    [
      r.petId,
      input.recordedAt ?? null,
      r.accelX,
      r.accelY,
      r.accelZ,
      r.gyroX,
      r.gyroY,
      r.gyroZ,
      r.enclosureTempC,
      r.accelEnergy,
      r.gyroEnergy,
      r.jerk,
      r.heartRateBpm,
      r.hrvRmssdMs,
      r.signalQuality,
      r.restingHeartRate,
      r.artifactRejected,
      r.activity,
      r.movementCount,
      r.layingMinutes,
      r.standingMinutes,
      r.sleepingMinutes,
      r.activeMinutes,
    ],
  );

  const open = await query<{ count: string }>(
    `SELECT COUNT(*)::text AS count FROM health_alerts
     WHERE pet_id = $1 AND is_acknowledged = FALSE`,
    [r.petId],
  );
  const openCount = Number(open.rows[0]?.count ?? 0);

  const alertCandidates = evaluateAlerts(input, {
    highRestingHr: baseline.highRestingHrThreshold,
    lowHrv: baseline.lowHrvThreshold,
  });
  const createdAlerts = [];
  for (const a of alertCandidates) {
    const row = await query(
      `INSERT INTO health_alerts (pet_id, reason, severity, description, context_note)
       VALUES ($1,$2,$3,$4,$5)
       RETURNING id, pet_id AS "petId", reason, severity, description,
                 context_note AS "contextNote", created_at AS "createdAt",
                 is_acknowledged AS "isAcknowledged"`,
      [a.petId, a.reason, a.severity, a.description, a.contextNote],
    );
    createdAlerts.push(row.rows[0]);
  }

  const score = computeHealthScore(input, openCount + createdAlerts.length, {
    highRestingHr: baseline.highRestingHrThreshold,
    lowHrv: baseline.lowHrvThreshold,
  });
  await query(
    `INSERT INTO health_scores (pet_id, score, label, reasons)
     VALUES ($1,$2,$3,$4::jsonb)`,
    [r.petId, score.score, score.label, JSON.stringify(score.reasons)],
  );

  await upsertDailySummary(r.petId, score.score, createdAlerts.length);

  // Refresh personalized baselines every ~25 ingests worth of counter samples
  let refreshedBaseline = baseline;
  if (baseline.samples === 0 || Number(inserted.rows[0].id) % 25 === 0) {
    refreshedBaseline = await refreshBaseline(r.petId);
  }

  return {
    readingId: inserted.rows[0].id,
    activity: r.activity,
    alerts: createdAlerts,
    healthScore: score,
    baseline: refreshedBaseline,
  };
}

async function upsertDailySummary(
  petId: string,
  healthScore: number,
  newAlerts: number,
) {
  const latest = await query(
    `SELECT * FROM sensor_readings
     WHERE pet_id = $1
     ORDER BY recorded_at DESC
     LIMIT 1`,
    [petId],
  );
  const s = latest.rows[0];
  if (!s) return;

  const avg = await query(
    `SELECT
       COALESCE(AVG(NULLIF(heart_rate_bpm, 0)), 0) AS avg_bpm,
       COALESCE(AVG(NULLIF(hrv_rmssd_ms, 0)), 0) AS avg_hrv,
       COALESCE(AVG(resting_heart_rate), 0) AS avg_resting
     FROM sensor_readings
     WHERE pet_id = $1 AND recorded_at::date = CURRENT_DATE`,
    [petId],
  );

  const openToday = await query<{ count: string }>(
    `SELECT COUNT(*)::text AS count FROM health_alerts
     WHERE pet_id = $1 AND created_at::date = CURRENT_DATE`,
    [petId],
  );

  const sleepScore =
    s.sleeping_minutes >= 480
      ? 90
      : s.sleeping_minutes >= 400
        ? 80
        : s.sleeping_minutes >= 320
          ? 70
          : s.sleeping_minutes >= 240
            ? 55
            : 40;

  await query(
    `INSERT INTO daily_summaries (
      pet_id, day, sleep_minutes, active_minutes, laying_minutes, standing_minutes,
      awakenings, restless_minutes, sleep_score, avg_bpm, avg_resting_bpm, avg_hrv,
      movement_count, alert_count, health_score
    ) VALUES (
      $1, CURRENT_DATE, $2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14
    )
    ON CONFLICT (pet_id, day) DO UPDATE SET
      sleep_minutes = EXCLUDED.sleep_minutes,
      active_minutes = EXCLUDED.active_minutes,
      laying_minutes = EXCLUDED.laying_minutes,
      standing_minutes = EXCLUDED.standing_minutes,
      awakenings = EXCLUDED.awakenings,
      restless_minutes = EXCLUDED.restless_minutes,
      sleep_score = EXCLUDED.sleep_score,
      avg_bpm = EXCLUDED.avg_bpm,
      avg_resting_bpm = EXCLUDED.avg_resting_bpm,
      avg_hrv = EXCLUDED.avg_hrv,
      movement_count = EXCLUDED.movement_count,
      alert_count = EXCLUDED.alert_count,
      health_score = EXCLUDED.health_score`,
    [
      petId,
      s.sleeping_minutes,
      s.active_minutes,
      s.laying_minutes,
      s.standing_minutes,
      // Awakenings / restless are soft estimates from activity shifts, not invented from gravity energy
      Number(s.active_minutes) > 30 ? 2 : 1,
      Math.min(40, Math.max(0, Math.round(Number(s.active_minutes) * 0.15))),
      sleepScore,
      Number(avg.rows[0].avg_bpm),
      Number(avg.rows[0].avg_resting),
      Number(avg.rows[0].avg_hrv),
      s.movement_count,
      Number(openToday.rows[0].count) + newAlerts,
      healthScore,
    ],
  );
}

export async function latestReading(petId: string) {
  const res = await query(
    `SELECT
      id, pet_id AS "petId", recorded_at AS "recordedAt",
      accel_x AS "accelX", accel_y AS "accelY", accel_z AS "accelZ",
      gyro_x AS "gyroX", gyro_y AS "gyroY", gyro_z AS "gyroZ",
      enclosure_temp_c AS "enclosureTempC",
      accel_energy AS "accelEnergy", gyro_energy AS "gyroEnergy", jerk,
      heart_rate_bpm AS "heartRateBpm", hrv_rmssd_ms AS "hrvRmssdMs",
      signal_quality AS "signalQuality", resting_heart_rate AS "restingHeartRate",
      artifact_rejected AS "artifactRejected", activity,
      movement_count AS "movementCount", laying_minutes AS "layingMinutes",
      standing_minutes AS "standingMinutes", sleeping_minutes AS "sleepingMinutes",
      active_minutes AS "activeMinutes"
     FROM sensor_readings
     WHERE pet_id = $1
     ORDER BY recorded_at DESC
     LIMIT 1`,
    [petId],
  );
  return res.rows[0] ?? null;
}

export async function readingHistory(petId: string, limit = 60) {
  const res = await query(
    `SELECT
      id, pet_id AS "petId", recorded_at AS "recordedAt",
      heart_rate_bpm AS "heartRateBpm", hrv_rmssd_ms AS "hrvRmssdMs",
      signal_quality AS "signalQuality", activity,
      accel_energy AS "accelEnergy", jerk, artifact_rejected AS "artifactRejected"
     FROM sensor_readings
     WHERE pet_id = $1
     ORDER BY recorded_at DESC
     LIMIT $2`,
    [petId, limit],
  );
  return res.rows.reverse();
}

export async function latestScore(petId: string) {
  const res = await query(
    `SELECT score, label, reasons, computed_at AS "computedAt"
     FROM health_scores
     WHERE pet_id = $1
     ORDER BY computed_at DESC
     LIMIT 1`,
    [petId],
  );
  return res.rows[0] ?? null;
}

export async function weekSummaries(petId: string) {
  const res = await query(
    `SELECT
      day, sleep_minutes AS "sleepMinutes", active_minutes AS "activeMinutes",
      laying_minutes AS "layingMinutes", standing_minutes AS "standingMinutes",
      awakenings, restless_minutes AS "restlessMinutes", sleep_score AS "sleepScore",
      avg_bpm AS "avgBpm", avg_resting_bpm AS "avgRestingBpm", avg_hrv AS "avgHrv",
      movement_count AS "movementCount", alert_count AS "alertCount",
      health_score AS "healthScore"
     FROM daily_summaries
     WHERE pet_id = $1 AND day >= CURRENT_DATE - INTERVAL '6 days'
     ORDER BY day ASC`,
    [petId],
  );
  return res.rows;
}

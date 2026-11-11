import { query } from '../db/pool.js';

export interface PetBaseline {
  petId: string;
  restingHrAvg: number;
  restingHrStd: number;
  sleepMinutesAvg: number;
  hrvAvg: number;
  samples: number;
  updatedAt: string;
  highRestingHrThreshold: number;
  lowHrvThreshold: number;
}

function mapRow(row: Record<string, unknown>): PetBaseline {
  const restingHrAvg = Number(row.resting_hr_avg ?? 80);
  const restingHrStd = Number(row.resting_hr_std ?? 12);
  const hrvAvg = Number(row.hrv_avg ?? 30);
  return {
    petId: String(row.pet_id),
    restingHrAvg,
    restingHrStd,
    sleepMinutesAvg: Number(row.sleep_minutes_avg ?? 420),
    hrvAvg,
    samples: Number(row.samples ?? 0),
    updatedAt: String(row.updated_at),
    // Personalized thresholds
    highRestingHrThreshold: Math.max(110, Math.round(restingHrAvg + 2.2 * restingHrStd)),
    lowHrvThreshold: Math.max(12, Math.round(hrvAvg * 0.55)),
  };
}

export async function getBaseline(petId: string): Promise<PetBaseline | null> {
  const res = await query(`SELECT * FROM pet_baselines WHERE pet_id = $1`, [petId]);
  if (!res.rows[0]) return null;
  return mapRow(res.rows[0] as Record<string, unknown>);
}

export async function getOrCreateBaseline(petId: string): Promise<PetBaseline> {
  const existing = await getBaseline(petId);
  if (existing) return existing;
  await query(
    `INSERT INTO pet_baselines (pet_id) VALUES ($1)
     ON CONFLICT (pet_id) DO NOTHING`,
    [petId],
  );
  return (await getBaseline(petId))!;
}

/** Recompute from last 7 days resting samples. */
export async function refreshBaseline(petId: string): Promise<PetBaseline> {
  const stats = await query(
    `SELECT
       COUNT(*)::int AS samples,
       COALESCE(AVG(resting_heart_rate) FILTER (WHERE resting_heart_rate IS NOT NULL AND resting_heart_rate > 0), 80) AS rh_avg,
       COALESCE(STDDEV_POP(resting_heart_rate) FILTER (WHERE resting_heart_rate IS NOT NULL AND resting_heart_rate > 0), 12) AS rh_std,
       COALESCE(AVG(hrv_rmssd_ms) FILTER (WHERE hrv_rmssd_ms > 0 AND artifact_rejected = FALSE), 30) AS hrv_avg,
       COALESCE(AVG(sleeping_minutes), 420) AS sleep_avg
     FROM sensor_readings
     WHERE pet_id = $1
       AND recorded_at >= NOW() - INTERVAL '7 days'`,
    [petId],
  );

  const s = stats.rows[0];
  await query(
    `INSERT INTO pet_baselines (
      pet_id, resting_hr_avg, resting_hr_std, sleep_minutes_avg, hrv_avg, samples, updated_at
    ) VALUES ($1,$2,$3,$4,$5,$6,NOW())
    ON CONFLICT (pet_id) DO UPDATE SET
      resting_hr_avg = EXCLUDED.resting_hr_avg,
      resting_hr_std = EXCLUDED.resting_hr_std,
      sleep_minutes_avg = EXCLUDED.sleep_minutes_avg,
      hrv_avg = EXCLUDED.hrv_avg,
      samples = EXCLUDED.samples,
      updated_at = NOW()`,
    [
      petId,
      Number(s.rh_avg),
      Math.max(4, Number(s.rh_std)),
      Number(s.sleep_avg),
      Number(s.hrv_avg),
      Number(s.samples),
    ],
  );

  return (await getBaseline(petId))!;
}

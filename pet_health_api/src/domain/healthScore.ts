import type { HealthScore, SensorIngestInput } from '../types.js';
import { enrichReading } from './derive.js';

export interface ScoreThresholds {
  highRestingHr: number;
  lowHrv: number;
  lowHr: number;
  overExertionHr: number;
}

const DEFAULTS: ScoreThresholds = {
  highRestingHr: 130,
  lowHrv: 18,
  lowHr: 55,
  overExertionHr: 170,
};

export function computeHealthScore(
  raw: SensorIngestInput,
  openAlerts: number,
  thresholds: Partial<ScoreThresholds> = {},
): HealthScore {
  const t = { ...DEFAULTS, ...thresholds };
  const r = enrichReading(raw);
  let score = 100;
  const reasons: string[] = [];
  const resting = r.activity === 'sleeping' || r.activity === 'laying';
  const tracked =
    r.sleepingMinutes + r.activeMinutes + r.layingMinutes + r.standingMinutes;

  if (r.artifactRejected || r.signalQuality < 0.35) {
    score -= 8;
    reasons.push('Pulse signal quality is low');
  }

  if (!r.artifactRejected && r.signalQuality >= 0.4 && r.heartRateBpm > 0) {
    if (resting && r.heartRateBpm >= t.highRestingHr) {
      score -= 25;
      reasons.push(
        `High resting heart rate (${r.heartRateBpm} bpm, thr ${t.highRestingHr})`,
      );
    } else if (r.heartRateBpm >= t.overExertionHr) {
      score -= 12;
      reasons.push(`Very high exertion HR (${r.heartRateBpm} bpm)`);
    } else if (r.heartRateBpm < t.lowHr) {
      score -= 30;
      reasons.push(`Low heart rate (${r.heartRateBpm} bpm)`);
    }

    if (resting && r.hrvRmssdMs > 0 && r.hrvRmssdMs < t.lowHrv) {
      score -= 10;
      reasons.push(`Low resting HRV (thr ${t.lowHrv} ms)`);
    }
  }

  // Only score sleep once the collar has tracked a meaningful session
  if (tracked >= 180 && r.sleepingMinutes < 360) {
    score -= 8;
    reasons.push(`Sleep so far looks short (${r.sleepingMinutes} min tracked)`);
  }

  if (r.jerk > 18) {
    score -= 20;
    reasons.push('Possible fall/impact spike');
  }

  if (openAlerts > 0) {
    score -= Math.min(openAlerts * 5, 20);
    reasons.push(`${openAlerts} open alert(s)`);
  }

  score = Math.max(0, Math.min(100, score));
  const label =
    score >= 75 ? 'Healthy' : score >= 50 ? 'At Risk' : 'Unhealthy';

  if (reasons.length === 0) {
    reasons.push('Vitals and activity within expected ranges for context');
  }

  return {
    score,
    label,
    reasons,
    computedAt: new Date().toISOString(),
  };
}

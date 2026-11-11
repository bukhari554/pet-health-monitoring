import type { HealthScore, SensorIngestInput } from '../types.js';
import { enrichReading } from './derive.js';

export function computeHealthScore(
  raw: SensorIngestInput,
  openAlerts: number,
): HealthScore {
  const r = enrichReading(raw);
  let score = 100;
  const reasons: string[] = [];
  const resting = r.activity === 'sleeping' || r.activity === 'laying';

  if (r.artifactRejected || r.signalQuality < 0.35) {
    score -= 8;
    reasons.push('Pulse signal quality is low');
  }

  if (!r.artifactRejected && r.signalQuality >= 0.4) {
    if (resting && r.heartRateBpm >= 130) {
      score -= 25;
      reasons.push(`High resting heart rate (${r.heartRateBpm} bpm)`);
    } else if (r.heartRateBpm >= 170) {
      score -= 12;
      reasons.push(`Very high exertion HR (${r.heartRateBpm} bpm)`);
    } else if (r.heartRateBpm > 0 && r.heartRateBpm < 55) {
      score -= 30;
      reasons.push(`Low heart rate (${r.heartRateBpm} bpm)`);
    }

    if (resting && r.hrvRmssdMs > 0 && r.hrvRmssdMs < 18) {
      score -= 10;
      reasons.push('Low resting HRV');
    }
  }

  if (r.sleepingMinutes < 360) {
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

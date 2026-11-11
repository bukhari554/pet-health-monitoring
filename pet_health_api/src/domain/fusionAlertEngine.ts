import type { AlertReason, AlertSeverity, HealthAlert, SensorIngestInput } from '../types.js';
import { enrichReading } from './derive.js';

export interface AlertThresholds {
  highRestingHr: number;
  lowHrv: number;
  lowHr: number;
  overExertionHr: number;
}

const DEFAULTS: AlertThresholds = {
  highRestingHr: 130,
  lowHrv: 18,
  lowHr: 55,
  overExertionHr: 180,
};

const lastFired = new Map<string, number>();
const COOLDOWN_MS = 5 * 60 * 1000;

function key(petId: string, reason: AlertReason) {
  return `${petId}:${reason}`;
}

function maybe(
  petId: string,
  reason: AlertReason,
  severity: AlertSeverity,
  description: string,
  contextNote: string,
  now: number,
): Omit<HealthAlert, 'id' | 'createdAt' | 'isAcknowledged'> | null {
  const k = key(petId, reason);
  const last = lastFired.get(k);
  if (last && now - last < COOLDOWN_MS) return null;
  lastFired.set(k, now);
  return { petId, reason, severity, description, contextNote };
}

export function evaluateAlerts(
  raw: SensorIngestInput,
  thresholds: Partial<AlertThresholds> = {},
) {
  const t = { ...DEFAULTS, ...thresholds };
  const r = enrichReading(raw);
  const now = Date.now();
  const out: NonNullable<ReturnType<typeof maybe>>[] = [];
  const push = (
    reason: AlertReason,
    severity: AlertSeverity,
    description: string,
    contextNote: string,
  ) => {
    const item = maybe(r.petId, reason, severity, description, contextNote, now);
    if (item) out.push(item);
  };

  const resting = r.activity === 'sleeping' || r.activity === 'laying';

  if (r.signalQuality < 0.15 && r.accelEnergy < 0.4) {
    push(
      'collar_off',
      'warning',
      'No reliable pulse and very low motion — collar may be off or loose.',
      `Activity: ${r.activity}, quality: ${Math.round(r.signalQuality * 100)}%`,
    );
  }

  if (r.signalQuality >= 0.15 && r.signalQuality < 0.45 && !r.artifactRejected) {
    push(
      'low_signal_quality',
      'info',
      'Pulse signal is weak. Reposition collar for better contact.',
      `Quality ${Math.round(r.signalQuality * 100)}%`,
    );
  }

  if (!r.artifactRejected && r.signalQuality >= 0.4) {
    if (resting && r.heartRateBpm >= t.highRestingHr) {
      push(
        'high_resting_hr',
        'critical',
        `Heart rate ${r.heartRateBpm} bpm while ${r.activity} (threshold ${t.highRestingHr}).`,
        'Personalized resting baseline used when enough samples exist.',
      );
    } else if (r.activity === 'playing' && r.heartRateBpm >= t.overExertionHr) {
      push(
        'over_exertion',
        'warning',
        `Very high HR (${r.heartRateBpm} bpm) during play.`,
        `Jerk: ${r.jerk.toFixed(2)}`,
      );
    }

    if (r.heartRateBpm > 0 && r.heartRateBpm < t.lowHr) {
      push(
        'low_hr',
        'critical',
        `Heart rate dropped to ${r.heartRateBpm} bpm.`,
        `Context: ${r.activity}`,
      );
    }

    if (resting && r.hrvRmssdMs > 0 && r.hrvRmssdMs < t.lowHrv) {
      push(
        'low_hrv',
        'warning',
        `Low HRV (${Math.round(r.hrvRmssdMs)} ms) at rest (threshold ${t.lowHrv}).`,
        'Early-warning only — not a diagnosis.',
      );
    }
  }

  if (r.jerk > 18 || r.accelEnergy > 35) {
    push(
      'fall_suspected',
      'critical',
      'Sudden impact / orientation spike detected from MPU6050.',
      `Jerk ${r.jerk.toFixed(1)}, energy ${r.accelEnergy.toFixed(1)}`,
    );
  }

  if ((r.enclosureTempC ?? 0) >= 34 && r.heartRateBpm >= 140 && !resting) {
    push(
      'heat_stress_proxy',
      'warning',
      `Elevated enclosure temp (${(r.enclosureTempC ?? 0).toFixed(1)}°C) with high HR.`,
      'MPU onboard temp is enclosure proxy — not core body temp.',
    );
  }

  return out;
}

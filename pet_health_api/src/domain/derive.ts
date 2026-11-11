import type { ActivityClass, SensorIngestInput } from '../types.js';

const G = 9.81;

/** Motion energy = |‖a‖ − g|. Prefer device-provided accelEnergy when present. */
export function motionEnergy(input: SensorIngestInput): number {
  if (typeof input.accelEnergy === 'number' && Number.isFinite(input.accelEnergy)) {
    return Math.max(0, input.accelEnergy);
  }
  const mag = Math.sqrt(input.accelX ** 2 + input.accelY ** 2 + input.accelZ ** 2);
  return Math.abs(mag - G);
}

export function deriveActivity(input: SensorIngestInput): ActivityClass {
  if (input.activity) return input.activity;

  const dynamic = motionEnergy(input);
  const gyroE =
    input.gyroEnergy ??
    Math.sqrt(
      (input.gyroX ?? 0) ** 2 +
        (input.gyroY ?? 0) ** 2 +
        (input.gyroZ ?? 0) ** 2,
    );
  const ax = Math.abs(input.accelX);
  const ay = Math.abs(input.accelY);
  const az = Math.abs(input.accelZ);

  if (dynamic > 6 || gyroE > 3) return 'playing';
  if (dynamic > 1.8 || gyroE > 1.2) return 'walking';
  if (ay >= ax && ay >= az && ay > 7) return 'standing';
  if (az >= ax && az >= ay && az > 7) {
    if (dynamic < 0.35 && gyroE < 0.45) return 'sleeping';
    return 'laying';
  }
  if (dynamic < 0.35 && gyroE < 0.4) return 'sleeping';
  return 'unknown';
}

export function enrichReading(input: SensorIngestInput) {
  const accelEnergy = motionEnergy(input);
  const gyroEnergy =
    input.gyroEnergy ??
    Math.sqrt(
      (input.gyroX ?? 0) ** 2 +
        (input.gyroY ?? 0) ** 2 +
        (input.gyroZ ?? 0) ** 2,
    );
  const jerk = input.jerk ?? accelEnergy * 0.4;
  const activity = deriveActivity({ ...input, accelEnergy, gyroEnergy });

  return {
    ...input,
    gyroX: input.gyroX ?? 0,
    gyroY: input.gyroY ?? 0,
    gyroZ: input.gyroZ ?? 0,
    enclosureTempC: input.enclosureTempC ?? null,
    accelEnergy,
    gyroEnergy,
    jerk,
    hrvRmssdMs: input.hrvRmssdMs ?? 0,
    // Missing quality must not look healthy
    signalQuality: input.signalQuality ?? 0,
    restingHeartRate: input.restingHeartRate ?? null,
    artifactRejected: input.artifactRejected ?? input.heartRateBpm === 0,
    activity,
    movementCount: input.movementCount ?? 0,
    layingMinutes: input.layingMinutes ?? 0,
    standingMinutes: input.standingMinutes ?? 0,
    sleepingMinutes: input.sleepingMinutes ?? 0,
    activeMinutes: input.activeMinutes ?? 0,
  };
}

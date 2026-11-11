import type { ActivityClass, SensorIngestInput } from '../types.js';

export function deriveActivity(input: SensorIngestInput): ActivityClass {
  if (input.activity) return input.activity;

  const ax = Math.abs(input.accelX);
  const ay = Math.abs(input.accelY);
  const az = Math.abs(input.accelZ);
  const energy =
    input.accelEnergy ??
    Math.sqrt(input.accelX ** 2 + input.accelY ** 2 + input.accelZ ** 2);

  if (energy > 12) return 'playing';
  if (energy > 4 || ax > 2) return 'walking';
  if (ay > 8.5) return 'standing';
  if (az > 8) return 'laying';
  if (energy < 1.2) return 'sleeping';
  return 'unknown';
}

export function enrichReading(input: SensorIngestInput) {
  const accelEnergy =
    input.accelEnergy ??
    Math.sqrt(input.accelX ** 2 + input.accelY ** 2 + input.accelZ ** 2);
  const gyroEnergy =
    input.gyroEnergy ??
    Math.sqrt(
      (input.gyroX ?? 0) ** 2 +
        (input.gyroY ?? 0) ** 2 +
        (input.gyroZ ?? 0) ** 2,
    );
  const jerk = input.jerk ?? accelEnergy * 0.4;
  const activity = deriveActivity({ ...input, accelEnergy });

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
    signalQuality: input.signalQuality ?? 0.8,
    restingHeartRate: input.restingHeartRate ?? null,
    artifactRejected: input.artifactRejected ?? false,
    activity,
    movementCount: input.movementCount ?? 0,
    layingMinutes: input.layingMinutes ?? 0,
    standingMinutes: input.standingMinutes ?? 0,
    sleepingMinutes: input.sleepingMinutes ?? 0,
    activeMinutes: input.activeMinutes ?? 0,
  };
}

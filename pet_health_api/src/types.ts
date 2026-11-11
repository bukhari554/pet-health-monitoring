export type ActivityClass =
  | 'sleeping'
  | 'laying'
  | 'standing'
  | 'walking'
  | 'playing'
  | 'unknown';

export type AlertSeverity = 'info' | 'warning' | 'critical';

export type AlertReason =
  | 'high_resting_hr'
  | 'low_hr'
  | 'low_hrv'
  | 'fall_suspected'
  | 'collar_off'
  | 'over_exertion'
  | 'low_signal_quality'
  | 'heat_stress_proxy';

export interface SensorIngestInput {
  petId: string;
  recordedAt?: string;
  accelX: number;
  accelY: number;
  accelZ: number;
  gyroX?: number;
  gyroY?: number;
  gyroZ?: number;
  enclosureTempC?: number;
  accelEnergy?: number;
  gyroEnergy?: number;
  jerk?: number;
  heartRateBpm: number;
  hrvRmssdMs?: number;
  signalQuality?: number;
  restingHeartRate?: number | null;
  artifactRejected?: boolean;
  activity?: ActivityClass;
  movementCount?: number;
  layingMinutes?: number;
  standingMinutes?: number;
  sleepingMinutes?: number;
  activeMinutes?: number;
}

export interface SensorReading extends Required<
  Omit<SensorIngestInput, 'recordedAt' | 'restingHeartRate' | 'enclosureTempC'>
> {
  id: number;
  recordedAt: string;
  enclosureTempC: number | null;
  restingHeartRate: number | null;
}

export interface HealthAlert {
  id: string;
  petId: string;
  reason: AlertReason;
  severity: AlertSeverity;
  description: string;
  contextNote: string;
  createdAt: string;
  isAcknowledged: boolean;
}

export interface HealthScore {
  score: number;
  label: 'Healthy' | 'At Risk' | 'Unhealthy';
  reasons: string[];
  computedAt: string;
}

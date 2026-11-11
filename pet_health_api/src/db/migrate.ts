import { pool } from './pool.js';

const sql = `
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS pets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  breed TEXT NOT NULL,
  age INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sensor_readings (
  id BIGSERIAL PRIMARY KEY,
  pet_id UUID NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  accel_x DOUBLE PRECISION NOT NULL,
  accel_y DOUBLE PRECISION NOT NULL,
  accel_z DOUBLE PRECISION NOT NULL,
  gyro_x DOUBLE PRECISION NOT NULL DEFAULT 0,
  gyro_y DOUBLE PRECISION NOT NULL DEFAULT 0,
  gyro_z DOUBLE PRECISION NOT NULL DEFAULT 0,
  enclosure_temp_c DOUBLE PRECISION,
  accel_energy DOUBLE PRECISION NOT NULL DEFAULT 0,
  gyro_energy DOUBLE PRECISION NOT NULL DEFAULT 0,
  jerk DOUBLE PRECISION NOT NULL DEFAULT 0,
  heart_rate_bpm INT NOT NULL DEFAULT 0,
  hrv_rmssd_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
  signal_quality DOUBLE PRECISION NOT NULL DEFAULT 0,
  resting_heart_rate INT,
  artifact_rejected BOOLEAN NOT NULL DEFAULT FALSE,
  activity TEXT NOT NULL DEFAULT 'unknown',
  movement_count INT NOT NULL DEFAULT 0,
  laying_minutes INT NOT NULL DEFAULT 0,
  standing_minutes INT NOT NULL DEFAULT 0,
  sleeping_minutes INT NOT NULL DEFAULT 0,
  active_minutes INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sensor_readings_pet_time
  ON sensor_readings (pet_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS health_alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pet_id UUID NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
  reason TEXT NOT NULL,
  severity TEXT NOT NULL,
  description TEXT NOT NULL,
  context_note TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_health_alerts_pet_open
  ON health_alerts (pet_id, is_acknowledged, created_at DESC);

CREATE TABLE IF NOT EXISTS daily_summaries (
  id BIGSERIAL PRIMARY KEY,
  pet_id UUID NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
  day DATE NOT NULL,
  sleep_minutes INT NOT NULL DEFAULT 0,
  active_minutes INT NOT NULL DEFAULT 0,
  laying_minutes INT NOT NULL DEFAULT 0,
  standing_minutes INT NOT NULL DEFAULT 0,
  awakenings INT NOT NULL DEFAULT 0,
  restless_minutes INT NOT NULL DEFAULT 0,
  sleep_score INT NOT NULL DEFAULT 0,
  avg_bpm DOUBLE PRECISION NOT NULL DEFAULT 0,
  avg_resting_bpm DOUBLE PRECISION NOT NULL DEFAULT 0,
  avg_hrv DOUBLE PRECISION NOT NULL DEFAULT 0,
  movement_count INT NOT NULL DEFAULT 0,
  alert_count INT NOT NULL DEFAULT 0,
  health_score INT NOT NULL DEFAULT 0,
  UNIQUE (pet_id, day)
);

CREATE TABLE IF NOT EXISTS health_scores (
  id BIGSERIAL PRIMARY KEY,
  pet_id UUID NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
  score INT NOT NULL,
  label TEXT NOT NULL,
  reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
  computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_health_scores_pet_time
  ON health_scores (pet_id, computed_at DESC);

CREATE TABLE IF NOT EXISTS pet_baselines (
  pet_id UUID PRIMARY KEY REFERENCES pets(id) ON DELETE CASCADE,
  resting_hr_avg DOUBLE PRECISION NOT NULL DEFAULT 80,
  resting_hr_std DOUBLE PRECISION NOT NULL DEFAULT 12,
  sleep_minutes_avg DOUBLE PRECISION NOT NULL DEFAULT 420,
  hrv_avg DOUBLE PRECISION NOT NULL DEFAULT 30,
  samples INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
`;

async function migrate() {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query(sql);
    await client.query('COMMIT');
    console.log('Migration complete');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Migration failed', err);
    process.exitCode = 1;
  } finally {
    client.release();
    await pool.end();
  }
}

migrate();

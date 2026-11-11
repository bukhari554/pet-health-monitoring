import { pool } from './pool.js';

const sql = `
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

async function main() {
  await pool.query(sql);
  console.log('Baselines migration complete');
  await pool.end();
}

main().catch(async (err) => {
  console.error(err);
  await pool.end();
  process.exitCode = 1;
});

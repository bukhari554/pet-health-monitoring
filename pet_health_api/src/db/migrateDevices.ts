import { pool } from './pool.js';

const sql = `
CREATE TABLE IF NOT EXISTS devices (
  serial TEXT PRIMARY KEY,
  token TEXT NOT NULL,
  pet_id UUID REFERENCES pets(id) ON DELETE SET NULL,
  online BOOLEAN NOT NULL DEFAULT FALSE,
  last_seen TIMESTAMPTZ,
  wifi_rssi INT,
  mqtt_up BOOLEAN NOT NULL DEFAULT FALSE,
  firmware TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO devices (serial, token, pet_id, firmware)
VALUES (
  'PET-0001',
  'dev-device-token',
  '11111111-1111-1111-1111-111111111111',
  'pet-health-esp32'
)
ON CONFLICT (serial) DO UPDATE SET
  token = EXCLUDED.token,
  pet_id = COALESCE(devices.pet_id, EXCLUDED.pet_id),
  updated_at = NOW();
`;

async function main() {
  await pool.query(sql);
  console.log('Devices migration + seed complete');
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exitCode = 1;
});

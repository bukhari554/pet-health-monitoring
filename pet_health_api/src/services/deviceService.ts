import { query } from '../db/pool.js';

export const OFFLINE_GRACE_MS = 20_000;

export interface DeviceRow {
  serial: string;
  token: string;
  petId: string | null;
  online: boolean;
  lastSeen: string | null;
  wifiRssi: number | null;
  mqttUp: boolean;
  firmware: string | null;
}

function map(row: Record<string, unknown>): DeviceRow {
  return {
    serial: String(row.serial),
    token: String(row.token),
    petId: row.pet_id ? String(row.pet_id) : null,
    online: Boolean(row.online),
    lastSeen: row.last_seen ? String(row.last_seen) : null,
    wifiRssi: row.wifi_rssi == null ? null : Number(row.wifi_rssi),
    mqttUp: Boolean(row.mqtt_up),
    firmware: row.firmware ? String(row.firmware) : null,
  };
}

export async function getDevice(serial: string) {
  const res = await query(`SELECT * FROM devices WHERE serial = $1`, [serial]);
  return res.rows[0] ? map(res.rows[0] as Record<string, unknown>) : null;
}

export async function listDevices() {
  await markStaleOffline();
  const res = await query(`SELECT * FROM devices ORDER BY serial`);
  return res.rows.map((r) => map(r as Record<string, unknown>));
}

export async function authenticateDevice(serial: string, token: string) {
  const d = await getDevice(serial);
  if (!d) return null;
  if (d.token !== token) return null;
  return d;
}

export async function touchDevice(
  serial: string,
  patch: { wifiRssi?: number; mqttUp?: boolean; firmware?: string } = {},
) {
  await query(
    `UPDATE devices SET
       online = TRUE,
       last_seen = NOW(),
       wifi_rssi = COALESCE($2, wifi_rssi),
       mqtt_up = COALESCE($3, mqtt_up),
       firmware = COALESCE($4, firmware),
       updated_at = NOW()
     WHERE serial = $1`,
    [
      serial,
      patch.wifiRssi ?? null,
      patch.mqttUp ?? null,
      patch.firmware ?? null,
    ],
  );
  return getDevice(serial);
}

export async function setMqttDown(serial: string) {
  await query(
    `UPDATE devices SET mqtt_up = FALSE, updated_at = NOW() WHERE serial = $1`,
    [serial],
  );
}

/** GlucoTouch-style grace: only mark offline after 20s without last_seen. */
export async function markStaleOffline() {
  await query(
    `UPDATE devices
     SET online = FALSE, mqtt_up = FALSE, updated_at = NOW()
     WHERE online = TRUE
       AND (last_seen IS NULL OR last_seen < NOW() - ($1 || ' milliseconds')::interval)`,
    [String(OFFLINE_GRACE_MS)],
  );
}

export async function getConnectivitySummary(serial: string) {
  await markStaleOffline();
  const d = await getDevice(serial);
  if (!d) return null;
  const lastMs = d.lastSeen ? Date.parse(d.lastSeen) : 0;
  const ageMs = lastMs ? Date.now() - lastMs : null;
  return {
    ...d,
    cloud: d.online && d.mqttUp,
    ageMs,
    graceMs: OFFLINE_GRACE_MS,
    keepaliveHintSec: 15,
    mqttKeepaliveSec: 20,
  };
}

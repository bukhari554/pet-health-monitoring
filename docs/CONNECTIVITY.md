# Pet Health — GlucoTouch-style connectivity

## Path
`Phone BLE → ESP32 (creds) → WiFi → mqtt://PC:1883 → Aedes → PostgreSQL → App`

1. Device advertises as `PetHealth-<serial>` (BLE).
2. App Settings → **Scan BLE** → connect → enter WiFi SSID/password + PC broker IP → **Apply over BLE**.
3. Device stores creds in NVS, joins WiFi, opens MQTT (CLOUD).
4. If MQTT drops, HTTP heartbeat + `/ingest` fallback until heal.

## Auth
- MQTT username = device serial (`PET-0001`)
- MQTT password = device token (`dev-device-token`)

## Topics
- `pet/<serial>/status` — every 15s keepalive
- `pet/<serial>/evt` — sensor readings
- `pet/<serial>/cmd` — reserved for app→device commands

## Cadence
- MQTT keepalive: 20s
- Status publish: 15s
- Heal reconnect: ~20s
- Backend offline grace: 20s

## BLE GATT (provisioning only)
- Service `7a5f0001-4d3e-4b2a-9c1d-8e7f6a5b4c3d`
- SSID / PASS / HOST / APPLY / STATUS notify

## App status labels
- **BLE** — phone linked for provisioning
- **WIFI** — device online on LAN (HTTP or pre-MQTT)
- **CLOUD** — MQTT up

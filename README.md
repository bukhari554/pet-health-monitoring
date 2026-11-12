# Pet Health Monitoring

AI-based pet health monitoring system using IoT + Android. An ESP32 collar reads pulse and IMU (MPU6050), provisions Wi‑Fi over BLE, then streams vitals to the cloud. The Android app shows live health score, vitals, alerts, and journal.

## Repo layout

| Folder | What it is |
|---|---|
| `pet_health_kotlin/` | Android app (Jetpack Compose) |
| `pet_health_esp32/` | ESP32 firmware (PlatformIO) |
| `pet_health_api/` | Backend API (Fastify / Postgres / MQTT) |

## Features

- BLE → Wi‑Fi provisioning (Gluco-compatible GATT protocol)
- Live heart rate, HRV, signal quality, activity / IMU
- Fusion alerts + health score
- Daily journal / sleep–activity summary
- Dark / Light / System theme

## Quick start

### Android app
```bash
cd pet_health_kotlin
./gradlew assembleDebug
```
APK: `pet_health_kotlin/app/build/outputs/apk/debug/app-debug.apk`

### ESP32 firmware
```bash
cd pet_health_esp32
python -m platformio run -e esp32dev -t upload --upload-port COMx
```

### API
```bash
cd pet_health_api
npm install
npm run dev
```

## Default device identity

- Serial: `PET-0001`
- Pet ID (Buddy): `11111111-1111-1111-1111-111111111111`

Do not commit real Wi‑Fi passwords, API tokens, or `local.properties`.

## GitHub Desktop

1. Open **GitHub Desktop**
2. **File → Add local repository**
3. Choose this folder: `PET HEALTH MONITORING`
4. **Publish repository** (keep private if you want)
5. Suggested repo name: `pet-health-monitoring`

## Suggested GitHub “About”

**Description:** IoT pet collar + Android app for live BPM/HRV/IMU monitoring, BLE Wi‑Fi setup, and cloud alerts.

**Topics:** `esp32` `android` `jetpack-compose` `iot` `mqtt` `pet-health` `ble` `fyp`

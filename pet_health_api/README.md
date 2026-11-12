# Pet Health API (Fastify + PostgreSQL + MQTT)

Backend for the Kotlin Pet Health Monitor app and ESP32 collar.

## Stack
- Node.js 20+ / Fastify
- PostgreSQL
- Embedded MQTT broker (TCP `1883` + WebSocket `9001/mqtt`)
- Fusion alerts, health score, journal, device connectivity

## Quick start (local)

1. Postgres DB:
```bash
psql -U postgres -c "CREATE DATABASE pet_health;"
```

2. Env:
```bash
copy .env.example .env
```

3. Install + setup + run:
```bash
npm install
npm run db:setup
npm run dev
```

API: `http://localhost:3000`  
Health: `GET /health`

## Docker / Dokploy

```bash
docker compose up -d --build
```

HTTP on host `3001` → container `3000` (see `docker-compose.yml`).

## Main endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | liveness + MQTT ports |
| GET | `/pets` | list pets |
| POST | `/ingest` | ESP32/device push (`x-device-token`) |
| GET | `/pets/:petId/vitals/latest` | latest vitals + score |
| GET | `/pets/:petId/vitals/history` | history |
| GET | `/pets/:petId/health-score` | score |
| GET | `/pets/:petId/journal` | 7-day summaries |
| GET | `/pets/:petId/alerts` | open alerts |
| POST | `/alerts/:id/acknowledge` | ack one |
| POST | `/pets/:petId/alerts/acknowledge-all` | ack all |
| GET | `/pets/:petId/baseline` | personalized baseline |
| POST | `/pets/:petId/baseline/refresh` | recompute baseline |
| GET | `/devices` | collar devices |
| GET | `/devices/:serial/connectivity` | online / MQTT / cloud |
| POST | `/devices/:serial/heartbeat` | HTTP fallback heartbeat |

## Device ingest example

```bash
curl -X POST http://localhost:3000/ingest ^
  -H "content-type: application/json" ^
  -H "x-device-token: dev-device-token" ^
  -d "{\"petId\":\"11111111-1111-1111-1111-111111111111\",\"accelX\":0.2,\"accelY\":0.1,\"accelZ\":9.6,\"heartRateBpm\":88,\"hrvRmssdMs\":32,\"signalQuality\":0.9,\"activity\":\"laying\",\"sleepingMinutes\":340}"
```

Seeded IDs:
- Buddy: `11111111-1111-1111-1111-111111111111`
- Milo: `22222222-2222-2222-2222-222222222222`
- Device serial: `PET-0001`

## Scripts

| Script | Purpose |
|--------|---------|
| `npm run typecheck` | TypeScript check |
| `npm run db:setup` | migrate + seed + devices |
| `npm run dev` | watch server |
| `npm run build` / `npm start` | production |
| `npm run simulate:device` | fake collar ingest loop |

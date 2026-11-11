# Pet Health API (Fastify + PostgreSQL)

Backend for the Kotlin Pet Health Monitor app and ESP32 collar.

## Stack
- Node.js + Fastify
- PostgreSQL
- Zod validation
- Domain: fusion alerts + explainable health score + daily journal

## Setup

1. Create DB:
```bash
psql -U postgres -c "CREATE DATABASE pet_health;"
```

2. Copy env:
```bash
copy .env.example .env
```

3. Install + migrate + seed:
```bash
npm install
npm run db:migrate
npm run db:seed
npm run dev
```

API: `http://localhost:3000`

## Main endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | liveness |
| GET | `/pets` | list pets |
| POST | `/ingest` | ESP32/device push (`x-device-token`) |
| GET | `/pets/:petId/vitals/latest` | latest vitals + score |
| GET | `/pets/:petId/vitals/history` | BPM/activity history |
| GET | `/pets/:petId/health-score` | latest explainable score |
| GET | `/pets/:petId/journal` | 7-day summaries |
| GET | `/pets/:petId/alerts` | open alerts |
| POST | `/alerts/:id/acknowledge` | ack one |
| POST | `/pets/:petId/alerts/acknowledge-all` | ack all |

## Device ingest example

```bash
curl -X POST http://localhost:3000/ingest ^
  -H "content-type: application/json" ^
  -H "x-device-token: dev-device-token" ^
  -d "{\"petId\":\"11111111-1111-1111-1111-111111111111\",\"accelX\":0.2,\"accelY\":0.1,\"accelZ\":9.6,\"heartRateBpm\":88,\"hrvRmssdMs\":32,\"signalQuality\":0.9,\"activity\":\"laying\",\"sleepingMinutes\":340}"
```

Seeded pet IDs:
- Buddy: `11111111-1111-1111-1111-111111111111`
- Milo: `22222222-2222-2222-2222-222222222222`

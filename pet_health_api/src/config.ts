import 'dotenv/config';

export const config = {
  port: Number(process.env.PORT ?? 3000),
  databaseUrl:
    process.env.DATABASE_URL ??
    'postgres://postgres:postgres@localhost:5432/pet_health',
  corsOrigin: process.env.CORS_ORIGIN ?? '*',
  deviceIngestToken: process.env.DEVICE_INGEST_TOKEN ?? 'dev-device-token',
  mqttPort: Number(process.env.MQTT_PORT ?? 1883),
  mqttWsPort: Number(process.env.MQTT_WS_PORT ?? 9001),
  deviceSerial: process.env.DEVICE_SERIAL ?? 'PET-0001',
};

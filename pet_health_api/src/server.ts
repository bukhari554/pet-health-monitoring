import Fastify from 'fastify';
import cors from '@fastify/cors';
import sensible from '@fastify/sensible';
import { config } from './config.js';
import { pool } from './db/pool.js';
import { startMqttBroker } from './mqtt/broker.js';
import { alertRoutes } from './routes/alerts.js';
import { baselineRoutes } from './routes/baselines.js';
import { deviceRoutes } from './routes/devices.js';
import { petRoutes } from './routes/pets.js';
import { sensorRoutes } from './routes/sensors.js';

async function main() {
  const app = Fastify({ logger: true });

  await app.register(cors, {
    origin: config.corsOrigin === '*' ? true : config.corsOrigin,
  });
  await app.register(sensible);

  app.get('/health', async () => ({
    ok: true,
    service: 'pet-health-api',
    mqttTcp: config.mqttPort,
    mqttWs: config.mqttWsPort,
  }));

  await app.register(petRoutes);
  await app.register(sensorRoutes);
  await app.register(alertRoutes);
  await app.register(baselineRoutes);
  await app.register(deviceRoutes);

  try {
    await pool.query('SELECT 1');
  } catch (err) {
    app.log.error(err, 'Postgres connection failed');
    process.exit(1);
  }

  await startMqttBroker(app.log);

  await app.listen({ port: config.port, host: '0.0.0.0' });
  app.log.info(
    {
      rest: `http://0.0.0.0:${config.port}`,
      mqtt: `mqtt://0.0.0.0:${config.mqttPort}`,
      mqttWs: `ws://0.0.0.0:${config.mqttWsPort}/mqtt`,
    },
    'Pet Health connectivity stack ready',
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

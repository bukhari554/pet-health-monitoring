import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import {
  getConnectivitySummary,
  listDevices,
  touchDevice,
  authenticateDevice,
} from '../services/deviceService.js';

export async function deviceRoutes(app: FastifyInstance) {
  app.get('/devices', async () => listDevices());

  app.get<{ Params: { serial: string } }>(
    '/devices/:serial/connectivity',
    async (req, reply) => {
      const summary = await getConnectivitySummary(req.params.serial);
      if (!summary) return reply.notFound('Device not found');
      return summary;
    },
  );

  // HTTPS/HTTP heartbeat when MQTT is down (GlucoTouch pattern)
  app.post<{ Params: { serial: string } }>(
    '/devices/:serial/heartbeat',
    async (req, reply) => {
      const token = String(req.headers['x-device-token'] ?? '');
      const auth = await authenticateDevice(req.params.serial, token);
      if (!auth) return reply.unauthorized('Invalid device token');

      const body = z
        .object({
          wifiRssi: z.number().optional(),
          firmware: z.string().optional(),
          mqttUp: z.boolean().optional(),
        })
        .safeParse(req.body ?? {});

      const device = await touchDevice(req.params.serial, {
        wifiRssi: body.success ? body.data.wifiRssi : undefined,
        firmware: body.success ? body.data.firmware : undefined,
        mqttUp: body.success ? body.data.mqttUp : false,
      });
      return device;
    },
  );
}

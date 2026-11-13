import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { config } from '../config.js';
import {
  getDeviceByPetId,
  LIVE_READING_MS,
  markStaleOffline,
} from '../services/deviceService.js';
import {
  ingestReading,
  latestReading,
  latestScore,
  readingHistory,
  weekSummaries,
} from '../services/sensorService.js';

const ingestSchema = z.object({
  petId: z.string().uuid(),
  recordedAt: z.string().min(1).optional(),
  accelX: z.number(),
  accelY: z.number(),
  accelZ: z.number(),
  gyroX: z.number().optional(),
  gyroY: z.number().optional(),
  gyroZ: z.number().optional(),
  enclosureTempC: z.number().optional(),
  accelEnergy: z.number().optional(),
  gyroEnergy: z.number().optional(),
  jerk: z.number().optional(),
  heartRateBpm: z.number().int().min(0).max(300),
  hrvRmssdMs: z.number().optional(),
  signalQuality: z.number().min(0).max(1).optional(),
  restingHeartRate: z.number().int().nullable().optional(),
  artifactRejected: z.boolean().optional(),
  activity: z
    .enum(['sleeping', 'laying', 'standing', 'walking', 'playing', 'unknown'])
    .optional(),
  movementCount: z.number().int().optional(),
  layingMinutes: z.number().int().optional(),
  standingMinutes: z.number().int().optional(),
  sleepingMinutes: z.number().int().optional(),
  activeMinutes: z.number().int().optional(),
});

export async function sensorRoutes(app: FastifyInstance) {
  app.post('/ingest', async (req, reply) => {
    const token = req.headers['x-device-token'];
    if (token !== config.deviceIngestToken) {
      return reply.unauthorized('Invalid device token');
    }

    const parsed = ingestSchema.safeParse(req.body);
    if (!parsed.success) {
      return reply.badRequest(parsed.error.message);
    }

    const result = await ingestReading(parsed.data);
    return reply.code(201).send(result);
  });

  app.get<{ Params: { petId: string } }>(
    '/pets/:petId/vitals/latest',
    async (req, reply) => {
      await markStaleOffline();
      const reading = await latestReading(req.params.petId);
      if (!reading) return reply.notFound('No readings yet');
      const score = await latestScore(req.params.petId);
      const device = await getDeviceByPetId(req.params.petId);
      const recordedAt = String(
        (reading as { recordedAt?: string }).recordedAt ?? '',
      );
      const recordedMs = recordedAt ? Date.parse(recordedAt) : NaN;
      const ageMs = Number.isFinite(recordedMs) ? Date.now() - recordedMs : null;
      const deviceOnline = Boolean(device?.online);
      const cloud = Boolean(device?.online && device?.mqttUp);
      // Live only when collar is online AND reading is fresh — stops stale/offline UI drift.
      const live =
        deviceOnline && ageMs != null && ageMs >= 0 && ageMs <= LIVE_READING_MS;
      return {
        reading,
        healthScore: score,
        live,
        ageMs,
        liveWithinMs: LIVE_READING_MS,
        deviceOnline,
        cloud,
        deviceSerial: device?.serial ?? null,
        lastSeen: device?.lastSeen ?? null,
      };
    },
  );

  app.get<{
    Params: { petId: string };
    Querystring: { limit?: string };
  }>('/pets/:petId/vitals/history', async (req) => {
    const limit = Math.min(Number(req.query.limit ?? 60), 500);
    return readingHistory(req.params.petId, limit);
  });

  app.get<{ Params: { petId: string } }>(
    '/pets/:petId/health-score',
    async (req, reply) => {
      const score = await latestScore(req.params.petId);
      if (!score) return reply.notFound('No score yet');
      return score;
    },
  );

  app.get<{ Params: { petId: string } }>(
    '/pets/:petId/journal',
    async (req) => weekSummaries(req.params.petId),
  );
}

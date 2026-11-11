import type { FastifyInstance } from 'fastify';
import { getOrCreateBaseline, refreshBaseline } from '../services/baselineService.js';

export async function baselineRoutes(app: FastifyInstance) {
  app.get<{ Params: { petId: string } }>(
    '/pets/:petId/baseline',
    async (req) => getOrCreateBaseline(req.params.petId),
  );

  app.post<{ Params: { petId: string } }>(
    '/pets/:petId/baseline/refresh',
    async (req) => refreshBaseline(req.params.petId),
  );
}

import type { FastifyInstance } from 'fastify';
import {
  acknowledgeAlert,
  acknowledgeAll,
  listAlerts,
} from '../services/alertService.js';

export async function alertRoutes(app: FastifyInstance) {
  app.get<{
    Params: { petId: string };
    Querystring: { open?: string };
  }>('/pets/:petId/alerts', async (req) => {
    const onlyOpen = req.query.open !== 'false';
    return listAlerts(req.params.petId, onlyOpen);
  });

  app.post<{ Params: { id: string } }>(
    '/alerts/:id/acknowledge',
    async (req, reply) => {
      const alert = await acknowledgeAlert(req.params.id);
      if (!alert) return reply.notFound('Alert not found');
      return alert;
    },
  );

  app.post<{ Params: { petId: string } }>(
    '/pets/:petId/alerts/acknowledge-all',
    async (req) => acknowledgeAll(req.params.petId),
  );
}

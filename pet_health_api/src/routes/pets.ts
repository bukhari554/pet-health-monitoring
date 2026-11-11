import type { FastifyInstance } from 'fastify';
import { getPet, listPets } from '../services/petService.js';

export async function petRoutes(app: FastifyInstance) {
  app.get('/pets', async () => listPets());

  app.get<{ Params: { id: string } }>('/pets/:id', async (req, reply) => {
    const pet = await getPet(req.params.id);
    if (!pet) return reply.notFound('Pet not found');
    return pet;
  });
}

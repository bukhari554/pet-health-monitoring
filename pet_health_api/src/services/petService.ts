import { query } from '../db/pool.js';

export async function listPets() {
  const res = await query(
    `SELECT id, name, type, breed, age, created_at AS "createdAt"
     FROM pets
     ORDER BY name ASC`,
  );
  return res.rows;
}

export async function getPet(id: string) {
  const res = await query(
    `SELECT id, name, type, breed, age, created_at AS "createdAt"
     FROM pets WHERE id = $1`,
    [id],
  );
  return res.rows[0] ?? null;
}

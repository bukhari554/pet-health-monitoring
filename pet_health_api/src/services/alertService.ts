import { query } from '../db/pool.js';

export async function listAlerts(petId: string, onlyOpen = true) {
  const res = await query(
    `SELECT id, pet_id AS "petId", reason, severity, description,
            context_note AS "contextNote", created_at AS "createdAt",
            is_acknowledged AS "isAcknowledged"
     FROM health_alerts
     WHERE pet_id = $1
       AND ($2::boolean = FALSE OR is_acknowledged = FALSE)
     ORDER BY created_at DESC
     LIMIT 100`,
    [petId, onlyOpen],
  );
  return res.rows;
}

export async function acknowledgeAlert(id: string) {
  const res = await query(
    `UPDATE health_alerts SET is_acknowledged = TRUE
     WHERE id = $1
     RETURNING id, pet_id AS "petId", reason, severity, description,
               context_note AS "contextNote", created_at AS "createdAt",
               is_acknowledged AS "isAcknowledged"`,
    [id],
  );
  return res.rows[0] ?? null;
}

export async function acknowledgeAll(petId: string) {
  await query(
    `UPDATE health_alerts SET is_acknowledged = TRUE
     WHERE pet_id = $1 AND is_acknowledged = FALSE`,
    [petId],
  );
  return { ok: true };
}

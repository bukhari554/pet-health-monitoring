import { pool, query } from './pool.js';

async function seed() {
  const existing = await query<{ count: string }>('SELECT COUNT(*)::text AS count FROM pets');
  if (Number(existing.rows[0]?.count ?? 0) > 0) {
    console.log('Pets already seeded');
    await pool.end();
    return;
  }

  await query(
    `INSERT INTO pets (id, name, type, breed, age) VALUES
      ('11111111-1111-1111-1111-111111111111', 'Buddy', 'Dog', 'Labrador', 3),
      ('22222222-2222-2222-2222-222222222222', 'Milo', 'Cat', 'Persian', 2)`,
  );

  console.log('Seeded Buddy + Milo');
  await pool.end();
}

seed().catch(async (err) => {
  console.error(err);
  await pool.end();
  process.exitCode = 1;
});

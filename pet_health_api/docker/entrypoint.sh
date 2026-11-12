#!/bin/sh
set -e

echo "Waiting for Postgres..."
node --input-type=module <<'NODE'
import pg from 'pg';
const url = process.env.DATABASE_URL;
for (let i = 0; i < 60; i++) {
  const c = new pg.Client({ connectionString: url });
  try {
    await c.connect();
    await c.query('SELECT 1');
    await c.end();
    console.log('Postgres ready');
    process.exit(0);
  } catch {
    try { await c.end(); } catch {}
    await new Promise((r) => setTimeout(r, 2000));
  }
}
console.error('Postgres not ready');
process.exit(1);
NODE

echo "Running migrations + seed..."
# Order matters: pets schema → baselines table → seed pets → devices FK
node dist/db/migrate.js
node dist/db/migrateBaselines.js
node dist/db/seed.js
node dist/db/migrateDevices.js

echo "Starting API..."
exec node dist/server.js

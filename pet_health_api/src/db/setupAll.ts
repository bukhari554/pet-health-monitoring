/**
 * One-shot local/Dokploy bootstrap:
 * schema → baselines (noop if already in migrate) → seed pets → devices.
 */
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');

function run(script: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(
      process.platform === 'win32' ? 'npx.cmd' : 'npx',
      ['tsx', script],
      { cwd: root, stdio: 'inherit', shell: process.platform === 'win32' },
    );
    child.on('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${script} exited with ${code}`));
    });
  });
}

async function main() {
  await run('src/db/migrate.ts');
  await run('src/db/migrateBaselines.ts');
  await run('src/db/seed.ts');
  await run('src/db/migrateDevices.ts');
  console.log('DB setup complete');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

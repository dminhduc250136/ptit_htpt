/**
 * scripts/gen-api.mjs
 *
 * Codegen runner: for each backend service, fetches /v3/api-docs and writes
 * TypeScript types to src/types/api/{service}.generated.ts via openapi-typescript@7.13.0.
 *
 * Prerequisite: all 6 services running (`docker compose up -d` from repo root)
 * Usage: `npm run gen:api`
 *
 * Source: 04-RESEARCH.md Pattern 3.
 */

import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

// Direct per-service ports (bypass gateway so we get raw OpenAPI without gateway wrapping).
// Gateway rewrites /api/{service}/v3/api-docs → /v3/api-docs on the downstream service,
// so BOTH paths work and yield identical specs. Per-service direct is more resilient to
// gateway config drift during Phase 4.
const SERVICES = [
  { name: 'users',         url: 'http://localhost:8081/v3/api-docs' },
  { name: 'products',      url: 'http://localhost:8082/v3/api-docs' },
  { name: 'orders',        url: 'http://localhost:8083/v3/api-docs' },
  { name: 'payments',      url: 'http://localhost:8084/v3/api-docs' },
  { name: 'inventory',     url: 'http://localhost:8085/v3/api-docs' },
  { name: 'notifications', url: 'http://localhost:8086/v3/api-docs' },
];

const outDir = resolve(process.cwd(), 'src/types/api');
mkdirSync(outDir, { recursive: true });

for (const s of SERVICES) {
  const outFile = resolve(outDir, `${s.name}.generated.ts`);
  console.log(`→ ${s.name}  ${s.url}  →  ${outFile}`);
  execFileSync(
    'npx',
    ['--yes', 'openapi-typescript@7.13.0', s.url, '-o', outFile],
    { stdio: 'inherit', shell: process.platform === 'win32' },
  );
}
console.log('✓ All services regenerated. Commit src/types/api/*.generated.ts');

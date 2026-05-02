/**
 * Phase 9 / Plan 09-05 (TEST-01).
 * D-13: Login user thường + admin → save 2 storageState fixtures.
 *
 * Tests reuse qua test.use({ storageState: 'e2e/storageState/{user,admin}.json' })
 * → KHÔNG login lặp lại mỗi test → giảm flakiness + tăng speed.
 *
 * KHÔNG commit storageState (chứa real session cookie) — .gitignore.
 *
 * Seed credentials (V100__seed_dev_data.sql — confirmed 2026-04-27):
 *   admin: admin@tmdt.local / admin123
 *   user:  demo@tmdt.local  / admin123
 *
 * Override qua env vars nếu seed khác môi trường:
 *   E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD
 *   E2E_USER_EMAIL, E2E_USER_PASSWORD
 */
import { chromium, type FullConfig } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

const STATE_DIR = path.resolve(process.cwd(), 'e2e', 'storageState');

// CONFIRM credentials với seed-dev SQL V100__seed_dev_data.sql
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@tmdt.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123';
const USER_EMAIL = process.env.E2E_USER_EMAIL ?? 'demo@tmdt.local';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'admin123';

async function loginAndSave(email: string, password: string, outFile: string) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: 'http://localhost:3000' });
  const page = await context.newPage();
  await page.goto('/login');
  // Login page dùng controlled state → getByLabel là cách an toàn nhất
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu').fill(password);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 }),
    page.click('button[type="submit"]'),
  ]);
  // Verify cookies set (auth_present cookie là chỉ báo login thành công)
  const cookies = await context.cookies();
  const authPresent = cookies.find((c) => c.name === 'auth_present');
  if (!authPresent) {
    await browser.close();
    throw new Error(`[global-setup] Login failed for ${email} — auth_present cookie not set. Kiểm tra seed credentials hoặc backend đã chạy chưa.`);
  }
  await context.storageState({ path: outFile });
  await browser.close();
  // eslint-disable-next-line no-console
  console.log(`[global-setup] Saved storageState: ${outFile}`);
}

export default async function globalSetup(_config: FullConfig) {
  fs.mkdirSync(STATE_DIR, { recursive: true });
  // eslint-disable-next-line no-console
  console.log('[global-setup] Logging in user + admin...');
  await loginAndSave(USER_EMAIL, USER_PASSWORD, path.join(STATE_DIR, 'user.json'));
  await loginAndSave(ADMIN_EMAIL, ADMIN_PASSWORD, path.join(STATE_DIR, 'admin.json'));
  // eslint-disable-next-line no-console
  console.log('[global-setup] storageState saved for user + admin — ready to run specs.');
}

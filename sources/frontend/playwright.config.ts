import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  // Không pick up legacy file .bak và global-setup (không phải test)
  testIgnore: ['**/*.legacy.spec.ts.bak', '**/global-setup.ts'],
  // Phase 9 / Plan 09-05 (D-13): global setup login user + admin → save storageState
  globalSetup: require.resolve('./e2e/global-setup'),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: 'e2e/results.json' }]],
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'vi-VN',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

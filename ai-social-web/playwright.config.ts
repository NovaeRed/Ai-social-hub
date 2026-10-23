import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  outputDir: `test-results/run-${Date.now()}`,
  fullyParallel: false,
  workers: 1,
  use: { channel: 'msedge', headless: true },
  reporter: 'list',
  projects: [
    { name: 'demo', testMatch: 'ui.spec.ts', use: { baseURL: 'http://127.0.0.1:5190' } },
    { name: 'live', testMatch: 'live.spec.ts', use: { baseURL: 'http://127.0.0.1:5186' } },
  ],
  webServer: [
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5190 --strictPort',
      url: 'http://127.0.0.1:5190',
      reuseExistingServer: true,
      env: { VITE_DEMO_MODE: 'true' },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5186 --strictPort',
      url: 'http://127.0.0.1:5186',
      reuseExistingServer: true,
      env: { VITE_DEMO_MODE: 'false' },
    },
  ],
});

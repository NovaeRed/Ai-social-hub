import { defineConfig } from '@playwright/test';
const demoPort = Number(process.env.PLAYWRIGHT_DEMO_PORT || 5190);
const livePort = Number(process.env.PLAYWRIGHT_LIVE_PORT || 5186);
const demoUrl = `http://127.0.0.1:${demoPort}`;
const liveUrl = `http://127.0.0.1:${livePort}`;
export default defineConfig({
  testDir: './tests',
  outputDir: `test-results/run-${Date.now()}`,
  fullyParallel: false,
  workers: 1,
  use: { channel: 'msedge', headless: true },
  reporter: 'list',
  projects: [
    {
      name: 'demo',
      testMatch: ['ui.spec.ts', 'chat.spec.ts', 'theme.spec.ts'],
      use: { baseURL: demoUrl },
    },
    {
      name: 'live',
      testMatch: ['live.spec.ts', 'chat-contract.spec.ts'],
      use: { baseURL: liveUrl },
    },
  ],
  webServer: [
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${demoPort} --strictPort`,
      url: demoUrl,
      reuseExistingServer: true,
      env: { VITE_DEMO_MODE: 'true' },
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${livePort} --strictPort`,
      url: liveUrl,
      reuseExistingServer: true,
      env: { VITE_DEMO_MODE: 'false' },
    },
  ],
});

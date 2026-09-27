/* End-to-end tests: the real Electron app (tests/e2e).  One at a time --
   each starts its own window and engine process (the fake engine,
   tests/fake-engine/fake-engine.ts, unless HCS_E2E_ENGINE says otherwise).
   Needs a display; on a Linux machine without one:
   xvfb-run -a -s '-screen 0 2400x1400x24' npm run e2e */
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'tests/e2e',
  testMatch: '*.e2e.ts',
  workers: 1,
  timeout: 60_000,
  reporter: [['list']],
});

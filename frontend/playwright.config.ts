import { defineConfig } from '@playwright/test';

/**
 * Test E2E: girano contro il dev server Vite (:5173) e il backend Tomcat (:8081),
 * entrambi già attivi durante lo sviluppo — nessun webServer gestito da Playwright.
 */
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30000,
  retries: 0,
  // Un worker solo: le fasi lavorano tutte sui dati dello stesso tenant (ritenute,
  // F24, liquidazioni) e in parallelo si pestano i piedi. Così `npx playwright test`
  // esegue l'intera suite in sequenza senza dover passare --workers=1.
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'off',
  },
  reporter: [
    ['list'],
    ['html', {
      outputFolder: 'tests/e2e/report',
      open: 'never',
    }],
  ],
});

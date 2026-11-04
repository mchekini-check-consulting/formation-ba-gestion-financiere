import { defineConfig, devices } from '@playwright/test';

// Tests end-to-end (vrai navigateur + vrai backend) : make test-e2e.
// Prérequis : la base et le backend tournent sur le poste (« lance l'application en
// local », skill lancer-en-local). Le serveur de dev du frontend est démarré ici s'il
// ne tourne pas déjà ; il relaie /api vers le backend (proxy.conf.json), comme le
// gateway sur le cluster.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});

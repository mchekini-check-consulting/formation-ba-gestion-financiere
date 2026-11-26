import { defineConfig, devices } from '@playwright/test';

// Tests end-to-end (vrai navigateur + vrai backend) : make test-e2e.
// Prérequis : la base et le backend tournent sur le poste (« lance l'application en
// local », skill lancer-en-local), backend en mode test (./mvnw spring-boot:run l'active). Le serveur de dev du frontend est démarré ici s'il
// ne tourne pas déjà ; il relaie /api vers le backend (proxy.conf.json), comme le
// gateway sur le cluster.
//
// Variante sur un environnement en ligne : E2E_URL=<adresse du frontend> npx playwright test --ui
// Rien n'est démarré sur le poste ; les tests créent des cabinets fictifs dans cet environnement.
const CIBLE = process.env['E2E_URL'];

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // Les tests partagent les comptes de démonstration (blocage, mot de passe changé) :
  // un seul navigateur à la fois, fichiers dans l'ordre alphabétique.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: CIBLE ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: CIBLE
    ? undefined
    : {
        command: 'npm start',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 180_000,
      },
});

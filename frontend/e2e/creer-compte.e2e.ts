import { Page, expect, test } from '@playwright/test';
import { codeRecu } from './outils';

// US-36 Connaître le tarif et créer le compte de son cabinet (PTO-61), de l'écran jusqu'à la
// base. Chaque test prend un SIREN et un email jamais utilisés : la base peut déjà contenir
// des cabinets créés par une exécution précédente.
const MOT_DE_PASSE = 'Pointeo2026!!';
const MESSAGE_SIREN = 'Un cabinet existe déjà avec ce SIREN. Demandez à son administrateur de vous inviter.';

function nouveauSiren(): string {
  return String(100_000_000 + Math.floor(Math.random() * 900_000_000));
}

async function remplirFormulaire(page: Page, siren: string, email: string): Promise<void> {
  await page.getByLabel('Nom du cabinet').fill('Cabinet Test');
  await page.getByLabel('SIREN').fill(siren);
  await page.getByLabel('Prénom').fill('Claire');
  await page.getByLabel('Nom', { exact: true }).fill('Martin');
  await page.getByLabel('Email professionnel').fill(email);
  await page.getByLabel('Mobile').fill('06 12 34 56 78');
  await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
  await page.getByLabel('Nombre de dossiers prévus').fill('120');
  await page.getByLabel(/J'accepte les/).check();
  await page.getByRole('button', { name: 'Recevoir le code SMS' }).click();
}

async function creerCabinet(page: Page, siren: string, email: string): Promise<void> {
  await page.goto('/creer-compte');
  await remplirFormulaire(page, siren, email);
  await expect(page.getByRole('heading', { name: 'Confirmez votre mobile' })).toBeVisible();
  await page.getByLabel('Code reçu par SMS').fill(await codeRecu(page));
  await page.getByRole('button', { name: 'Valider et créer mon cabinet' }).click();
  await expect(page.getByRole('heading', { name: 'Cabinet Test est prêt' })).toBeVisible();
}

test.describe('US-36 Créer le compte de son cabinet', () => {
  test('CA1 — Claire crée Cabinet Test et en devient administratrice', async ({ page }) => {
    const siren = nouveauSiren();
    await page.goto('/creer-compte');
    await page.getByLabel('Nombre de dossiers prévus').fill('120');
    await expect(page.getByTestId('tarif')).toContainText('960 € HT par mois');

    await creerCabinet(page, siren, `claire.martin+${siren}@cabinet-test.fr`);
    await expect(page.getByText('Bienvenue Claire Martin')).toBeVisible();

    // Claire est connectée : elle arrive sur ses dossiers sans se reconnecter.
    await page.getByRole('link', { name: 'Accéder à mon cabinet' }).click();
    await expect(page).toHaveURL(/\/dossiers$/);
  });

  test('CA2 — un SIREN déjà utilisé par un cabinet est refusé', async ({ page }) => {
    const siren = nouveauSiren();
    await creerCabinet(page, siren, `premiere+${siren}@cabinet-test.fr`);

    await page.goto('/creer-compte');
    await remplirFormulaire(page, siren, `autre+${siren}@cabinet-test.fr`);
    await expect(page.getByTestId('alerte')).toHaveText(MESSAGE_SIREN);
    await expect(page.getByRole('heading', { name: 'Créer un compte' })).toBeVisible();
  });

  test('CA3 — le simulateur des tarifs donne 960 € HT par mois pour 120 dossiers', async ({ page }) => {
    await page.goto('/');
    const simulateur = page.locator('.simulator');
    await simulateur.getByRole('spinbutton').fill('120');
    await expect(simulateur.locator('.simulator__total')).toContainText('960 €');

    // Le nombre de dossiers suit dans le formulaire « Créer un compte ».
    await simulateur.getByRole('link', { name: 'Créer le compte de mon cabinet' }).click();
    await expect(page).toHaveURL(/\/creer-compte\?dossiers=120$/);
    await expect(page.getByTestId('tarif')).toContainText('960 € HT par mois');
  });
});

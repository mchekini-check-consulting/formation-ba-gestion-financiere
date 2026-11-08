import { expect, test } from '@playwright/test';

// US-03 Se connecter (PTO-4), de l'écran jusqu'à la base : comptes de démonstration
// de backend/src/main/resources/db/migration/V6__compte_donnees_demo.sql.
const EMAIL = 'jean.dupont@cabinet-test.fr';
const MOT_DE_PASSE = 'Pointeo2026!';
const MESSAGE_ERREUR = 'Email ou mot de passe incorrect';

test.describe('US-03 Se connecter', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/connexion');
    await expect(page.getByRole('heading', { name: 'Se connecter' })).toBeVisible();
  });

  test('CA1 — collaborateur : bon mot de passe -> écran du code reçu par email', async ({ page }) => {
    await page.getByLabel('Email').fill(EMAIL);
    await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL(/\/verification-code$/);
  });

  test('CA2 — mauvais mot de passe -> « Email ou mot de passe incorrect »', async ({ page }) => {
    await page.getByLabel('Email').fill(EMAIL);
    await page.getByLabel('Mot de passe', { exact: true }).fill('mauvais-mot-de-passe');
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByRole('alert')).toHaveText(MESSAGE_ERREUR);
    await expect(page).toHaveURL(/\/connexion$/);
    await expect(page.getByText('Ce champ est obligatoire')).toHaveCount(0);
  });

  test('RG1 — les majuscules de l\'email ne comptent pas', async ({ page }) => {
    await page.getByLabel('Email').fill('Jean.Dupont@Cabinet-Test.FR');
    await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL(/\/verification-code$/);
  });

  test('RG2 — email inconnu : même message, sans dire ce qui est faux', async ({ page }) => {
    await page.getByLabel('Email').fill('inconnu@cabinet-test.fr');
    await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByRole('alert')).toHaveText(MESSAGE_ERREUR);
  });

  test('dirigeant client : bon mot de passe -> écran du code reçu par email', async ({ page }) => {
    await page.getByLabel('Email').fill('sophie.martin@boulangerie-test.fr');
    await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL(/\/verification-code$/);
  });

  test('champs vides -> « Ce champ est obligatoire »', async ({ page }) => {
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByText('Ce champ est obligatoire')).toHaveCount(2);
    await expect(page).toHaveURL(/\/connexion$/);
  });
});

test('la page d\'accueil mène à l\'écran de connexion', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Se connecter' }).first().click();
  await expect(page).toHaveURL(/\/connexion$/);
  await expect(page.getByRole('heading', { name: 'Se connecter' })).toBeVisible();
});

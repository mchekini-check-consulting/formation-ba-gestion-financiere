import { expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-10 Créer un dossier client (PTO-17), de l'écran jusqu'à la base.
// Chaque test crée son propre cabinet (US-36) : Claire en est la collaboratrice connectée.
test.describe('US-10 Créer un dossier client', () => {
  test('CA1 — le dossier créé s’ouvre (RG3)', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Fleurs Martin EURL', '812 345 679');

    await expect(page).toHaveURL(/\/dossiers\/\d+$/);
    await expect(page.getByRole('heading', { name: 'Fleurs Martin EURL' })).toBeVisible();
    await expect(page.getByText('812 345 679')).toBeVisible();

    // Toujours là après rechargement : le dossier est enregistré en base.
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Fleurs Martin EURL' })).toBeVisible();
  });

  test('CA doublon — un SIREN déjà suivi par le cabinet est refusé', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Boulangerie Leroy SARL', '812345678');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);

    await creerDossier(page, 'Boulangerie Leroy', '812 345 678');
    await expect(page.getByRole('alert')).toHaveText('Un dossier existe déjà pour ce SIREN dans ce cabinet');
    await expect(page).toHaveURL(/\/dossiers\/nouveau$/);
  });

  test('RG2 — deux cabinets différents peuvent suivre la même entreprise', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Garage Morel SAS', '798654321');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await nouveauCabinet(page, 'Autre Cabinet');
    await creerDossier(page, 'Garage Morel SAS', '798654321');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);
  });

  test('RG1 — raison sociale et SIREN obligatoires', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, '', '123');
    await expect(page.getByText('Ce champ est obligatoire')).toBeVisible();
    await expect(page.getByText('Le SIREN comporte 9 chiffres')).toBeVisible();
  });
});

import { expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-09 Consulter la liste des dossiers du cabinet (PTO-16), de l'écran jusqu'à la base.
// Chaque test crée son propre cabinet (US-36) : Claire en est la collaboratrice connectée.
test.describe('US-09 Consulter la liste des dossiers du cabinet', () => {
  test('CA vide — « Aucun dossier pour le moment » et « Créer un dossier »', async ({ page }) => {
    await nouveauCabinet(page);
    await page.goto('/dossiers');

    await expect(page.getByText('Aucun dossier pour le moment')).toBeVisible();
    await page.locator('.vide').getByRole('link', { name: 'Créer un dossier' }).click();
    await expect(page).toHaveURL(/\/dossiers\/nouveau$/);
  });

  test('RG1 — chaque ligne : raison sociale, SIREN, factures, état de l’espace client ; un clic ouvre le dossier', async ({
    page,
  }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Garage Morel SAS', '798654321');
    await creerDossier(page, 'Boulangerie Leroy SARL', '812345678');
    await page.goto('/dossiers');

    const lignes = page.getByRole('row');
    await expect(lignes).toHaveCount(3); // en-tête + 2 dossiers, triés par raison sociale
    await expect(lignes.nth(1)).toContainText('Boulangerie Leroy SARL');
    await expect(lignes.nth(1)).toContainText('812 345 678');
    await expect(lignes.nth(1)).toContainText('0 facture');
    await expect(lignes.nth(1)).toContainText('Non invité');
    await expect(lignes.nth(2)).toContainText('Garage Morel SAS');

    await page.getByRole('link', { name: 'Garage Morel SAS' }).click();
    await expect(page.getByRole('heading', { name: 'Garage Morel SAS' })).toBeVisible();
  });

  test('RG2 — recherche par raison sociale ou par SIREN', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Garage Morel SAS', '798654321');
    await creerDossier(page, 'Boulangerie Leroy SARL', '812345678');
    await page.goto('/dossiers');

    const recherche = page.getByLabel('Rechercher un dossier');
    await recherche.fill('leroy');
    await expect(page.getByRole('row')).toHaveCount(2);
    await expect(page.getByRole('row').nth(1)).toContainText('Boulangerie Leroy SARL');

    await recherche.fill('798 654');
    await expect(page.getByRole('row')).toHaveCount(2);
    await expect(page.getByRole('row').nth(1)).toContainText('Garage Morel SAS');

    await recherche.fill('pâtisserie');
    await expect(page.getByText('Aucun dossier ne correspond à votre recherche')).toBeVisible();
  });

  test('RG3 — un cabinet ne voit jamais les dossiers d’un autre cabinet', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Fleurs Martin EURL', '812345679');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await nouveauCabinet(page, 'Autre Cabinet');
    await page.goto('/dossiers');
    await expect(page.getByText('Aucun dossier pour le moment')).toBeVisible();
    await expect(page.getByText('Fleurs Martin EURL')).toHaveCount(0);
  });
});

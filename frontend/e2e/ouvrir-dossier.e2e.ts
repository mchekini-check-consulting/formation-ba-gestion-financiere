import { expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-11 Ouvrir un dossier, de l'écran jusqu'à la base. Chaque test crée son propre cabinet.
test.describe('US-11 Ouvrir un dossier', () => {
  test('RG1 et RG2 — en-tête du dossier et onglets, ouvert depuis la liste', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Fleurs Martin EURL', '812345679');
    await page.getByRole('link', { name: '← Dossiers' }).click();
    await page.getByRole('link', { name: 'Fleurs Martin EURL' }).click();

    await expect(page).toHaveURL(/\/dossiers\/\d+$/);
    await expect(page.getByRole('heading', { name: 'Fleurs Martin EURL' })).toBeVisible();
    await expect(page.getByText('SIREN 812 345 679')).toBeVisible();
    await expect(page.getByText('Espace client : Non invité')).toBeVisible();

    const fournisseurs = page.getByRole('tab', { name: 'Factures fournisseurs' });
    const clients = page.getByRole('tab', { name: 'Factures clients' });
    await expect(fournisseurs).toHaveAttribute('aria-selected', 'true');
    await expect(clients).toHaveAttribute('aria-selected', 'false');
    await expect(page.getByRole('tab', { name: 'Modules' })).toBeVisible();

    await clients.click();
    await expect(clients).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel')).toContainText('Aucune facture client pour le moment');
  });

  test('RG3 — dossier d’un autre cabinet ou adresse tapée à la main', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Garage Morel SAS', '798654321');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);
    const adresseDuDossier = page.url();

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await nouveauCabinet(page, 'Autre Cabinet');
    await page.goto(adresseDuDossier);
    await expect(page.getByRole('alert')).toHaveText('Dossier introuvable ou accès non autorisé');
    await expect(page.getByText('Garage Morel SAS')).toHaveCount(0);

    await page.goto('/dossiers/abc');
    await expect(page.getByRole('alert')).toHaveText('Dossier introuvable ou accès non autorisé');
  });
});

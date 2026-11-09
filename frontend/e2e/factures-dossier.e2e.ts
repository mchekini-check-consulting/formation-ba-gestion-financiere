import { expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-12 Factures d'un dossier, de l'écran jusqu'à la base. Le dépôt (US-15) n'existant pas
// encore, les factures viennent du bouton du mode test (backend local lancé en mode test).
test.describe('US-12 Factures d’un dossier', () => {
  test('CA vide puis RG1, RG2, RG3 — achats et ventes, « Reçue », badge « Nouvelle »', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Fleurs Martin EURL', '812345679');

    // CA vide, sur les deux onglets.
    await expect(page.getByText('Aucune facture fournisseur pour le moment')).toBeVisible();
    await page.getByRole('tab', { name: 'Factures clients' }).click();
    await expect(page.getByText('Aucune facture client pour le moment')).toBeVisible();

    await page.getByRole('tab', { name: 'Factures fournisseurs' }).click();
    await page.getByRole('button', { name: "Ajouter des factures d'exemple (mode test)" }).click();

    // RG1 et RG2 : factures fournisseurs, la plus récente d'abord.
    const achats = page.getByRole('tabpanel').locator('tbody tr');
    await expect(achats).toHaveCount(2);
    await expect(achats.nth(0)).toContainText('ticket-fournitures.png');
    await expect(achats.nth(0)).toContainText('Claire Martin');
    await expect(achats.nth(0)).toContainText('Reçue');
    await expect(achats.nth(0)).toContainText(/\d{2}\/\d{2}\/\d{4} à \d{2}:\d{2}/);
    // RG3 : le ticket n'a jamais été ouvert, la facture d'électricité si.
    await expect(achats.nth(0).getByText('Nouvelle')).toBeVisible();
    await expect(achats.nth(1)).toContainText('facture-electricite-octobre.pdf');
    await expect(achats.nth(1).getByText('Nouvelle')).toHaveCount(0);

    await page.getByRole('tab', { name: 'Factures clients' }).click();
    const ventes = page.getByRole('tabpanel').locator('tbody tr');
    await expect(ventes).toHaveCount(1);
    await expect(ventes.nth(0)).toContainText('facture-client-0042.pdf');

    // US-09 : la liste des dossiers compte les 3 factures.
    await page.getByRole('link', { name: '← Dossiers' }).click();
    await expect(page.getByRole('row', { name: /Fleurs Martin EURL/ })).toContainText('3 factures');
  });
});

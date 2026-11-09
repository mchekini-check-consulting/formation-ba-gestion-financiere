import { Page, expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-13 Aperçu et téléchargement, de l'écran jusqu'à la base. Les factures viennent du bouton du
// mode test (le dépôt US-15 n'existant pas encore), avec un vrai PDF et une vraie image.
async function dossierAvecFactures(page: Page): Promise<void> {
  await nouveauCabinet(page);
  await creerDossier(page, 'Fleurs Martin EURL', '812345679');
  await page.getByRole('button', { name: "Ajouter des factures d'exemple (mode test)" }).click();
  await expect(page.getByRole('tabpanel').locator('tbody tr')).toHaveCount(2);
}

test.describe('US-13 Aperçu et téléchargement d’une facture', () => {
  test('RG1 et RG2 — aperçu de l’image, téléchargement, le badge « Nouvelle » disparaît', async ({ page }) => {
    await dossierAvecFactures(page);
    const ticket = page.getByRole('row', { name: /ticket-fournitures\.png/ });
    await expect(ticket.getByText('Nouvelle')).toBeVisible();

    await page.getByRole('link', { name: 'ticket-fournitures.png' }).click();
    await expect(page).toHaveURL(/\/dossiers\/\d+\/factures\/\d+$/);
    await expect(page.getByRole('heading', { name: 'ticket-fournitures.png' })).toBeVisible();
    const image = page.getByRole('img', { name: 'Aperçu de ticket-fournitures.png' });
    await expect(image).toBeVisible();
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(240);

    const telechargement = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Télécharger' }).click();
    expect((await telechargement).suggestedFilename()).toBe('ticket-fournitures.png');

    await page.getByRole('link', { name: '← Retour au dossier' }).click();
    await expect(page.getByRole('tab', { name: 'Factures fournisseurs' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('row', { name: /ticket-fournitures\.png/ }).getByText('Nouvelle')).toHaveCount(0);
  });

  test('RG1 et RG2 — aperçu du PDF d’une facture client, retour sur son onglet', async ({ page }) => {
    await dossierAvecFactures(page);
    await page.getByRole('tab', { name: 'Factures clients' }).click();
    await page.getByRole('link', { name: 'facture-client-0042.pdf' }).click();

    // Chrome sans écran (tests) n'a pas de lecteur PDF : le cadre d'aperçu y devient lui-même un
    // téléchargement. On attend donc celui qui porte le nom d'origine de la facture.
    const noms: string[] = [];
    page.on('download', (telechargement) => noms.push(telechargement.suggestedFilename()));
    await expect(page.locator('iframe[title="Aperçu de facture-client-0042.pdf"]')).toBeVisible();
    await page.getByRole('button', { name: 'Télécharger' }).click();
    await expect.poll(() => noms).toContain('facture-client-0042.pdf');

    await page.getByRole('link', { name: '← Retour au dossier' }).click();
    await expect(page.getByRole('tab', { name: 'Factures clients' })).toHaveAttribute('aria-selected', 'true');
  });

  test('RG3 — sans connexion : écran de connexion et aucun fichier ; autre cabinet : refus', async ({ page }) => {
    await dossierAvecFactures(page);
    await page.getByRole('link', { name: 'ticket-fournitures.png' }).click();
    await expect(page.getByRole('img', { name: 'Aperçu de ticket-fournitures.png' })).toBeVisible();
    const adresse = new URL(page.url()).pathname;

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await page.goto(adresse);
    await expect(page).toHaveURL(/\/connexion/);
    // Le fichier lui-même n'est jamais envoyé sans session.
    const sansSession = await page.request.get(`/api${adresse}/fichier`);
    expect(sansSession.status()).toBe(401);
    expect(sansSession.headers()['content-type']).toContain('application/json');

    await nouveauCabinet(page, 'Autre Cabinet');
    await page.goto(adresse);
    await expect(page.getByRole('alert')).toHaveText('Facture introuvable ou accès non autorisé');
    await expect(page.locator('img, iframe')).toHaveCount(0);
  });
});

import { Page, expect, test } from '@playwright/test';
import { FACTURE_EDF, adresseApi, creerDossier, nouveauCabinet } from './outils';

// Factures d'un dossier, de l'écran jusqu'à la base : import par le cabinet (US-52), liste (US-12,
// US-51, colonnes Tiers / Date / TTC), lecture automatique (US-53), aperçu (US-13) et fiche
// vérifiée puis validée (US-55), écritures générées (US-56). Facture : PDF EDF d'exemple du backend
// (lu sans Tesseract).

/** Nouveau cabinet, nouveau dossier, facture EDF importée et lue. */
async function factureEdfLue(page: Page): Promise<void> {
  await nouveauCabinet(page);
  await creerDossier(page, 'Fleurs Martin EURL', '812345679');
  await expect(page.getByText('Aucune facture fournisseur pour le moment')).toBeVisible();
  await page.getByLabel('Importer des factures').setInputFiles(FACTURE_EDF);
  // US-53 RG1 : la liste se met à jour toute seule à la fin de la lecture.
  await expect(ligneEdf(page)).toContainText('Lue', { timeout: 30_000 });
}

function ligneEdf(page: Page) {
  return page.getByRole('row', { name: /facture-electricite-octobre\.pdf/ });
}

test.describe('Factures d’un dossier', () => {
  test('US-52, US-12, US-53 — import, badge « Nouvelle », tiers, date et TTC lus', async ({ page }) => {
    await factureEdfLue(page);
    const ligne = ligneEdf(page);
    await expect(ligne.getByText('Nouvelle')).toBeVisible();
    await expect(ligne).toContainText('Import cabinet');
    await expect(ligne).toContainText('Reçue');
    await expect(ligne).toContainText('EDF');
    await expect(ligne).toContainText('12/06/2026');
    await expect(ligne).toContainText(/186,40\s€/);
  });

  test('US-13, US-55, US-56 — aperçu, correction au clic, validation : « Traitée » et écriture générée', async ({
    page,
  }) => {
    await factureEdfLue(page);
    await page.getByRole('link', { name: 'facture-electricite-octobre.pdf' }).click();
    await expect(page.getByRole('heading', { name: 'facture-electricite-octobre.pdf' })).toBeVisible();
    await expect(page.locator('iframe[title="Aperçu de facture-electricite-octobre.pdf"]')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Informations de la facture' })).toBeVisible();

    // RG2 : un clic sur le compte « À vérifier » ouvre la fiche en modification, curseur dessus.
    await page.getByRole('button', { name: 'Modifier : Compte de contrepartie' }).click();
    const compte = page.getByRole('combobox', { name: 'Compte de contrepartie' });
    await expect(compte).toBeFocused();
    await expect(compte).toHaveValue('606');

    // CA2 : champ obligatoire vidé -> refus, rien n'est enregistré.
    await page.getByLabel('Numéro de facture').fill('');
    await page.getByRole('button', { name: 'Valider', exact: true }).click();
    await expect(page.getByRole('alert')).toHaveText('Indiquez le numéro de facture');

    // CA1 : corrigé (numéro remis, nom du tiers changé) puis validé.
    await page.getByLabel('Numéro de facture').fill('F-2026-0611');
    await page.getByLabel('Nom du tiers').fill('EDF Entreprises');
    await page.getByRole('button', { name: 'Valider', exact: true }).click();
    await expect(page.getByText('Traitée', { exact: true })).toBeVisible();
    await expect(page.getByText(/^Modifiée par Claire Martin le/)).toBeVisible();

    // US-56 CA1 : écriture au journal des achats, équilibrée.
    const ecritures = page.locator('app-ecritures-facture');
    await expect(ecritures.getByRole('heading', { name: 'Écritures générées' })).toBeVisible();
    const lignes = ecritures.locator('tbody tr');
    await expect(lignes).toHaveCount(3);
    await expect(lignes.nth(0)).toContainText('606');
    await expect(lignes.nth(1)).toContainText('44566');
    await expect(lignes.nth(2)).toContainText('401');
    await expect(ecritures.locator('tfoot')).toContainText(/186,40\s€\s*186,40\s€/);

    // US-56 CA2 : facture d'achat traitée corrigée (écriture non exportée), puis écriture re-générée.
    await page.getByRole('button', { name: 'Corriger' }).click();
    await page.getByLabel('Montant HT').fill('160');
    await page.getByLabel('Montant TTC').fill('191,07');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(ecritures.getByText('À re-générer')).toBeVisible();
    await ecritures.getByRole('button', { name: 'Re-générer les écritures' }).click();
    await expect(ecritures.getByText('À re-générer')).toHaveCount(0);
    await expect(ecritures.locator('tfoot')).toContainText(/191,07\s€\s*191,07\s€/);

    await page.getByRole('link', { name: '← Retour au dossier' }).click();
    await expect(ligneEdf(page)).toContainText('Traitée');
    await expect(ligneEdf(page).getByText('Nouvelle')).toHaveCount(0);
  });

  test('US-13 RG3 — sans connexion : écran de connexion et aucun fichier ; autre cabinet : refus', async ({
    page,
  }) => {
    await factureEdfLue(page);
    await page.getByRole('link', { name: 'facture-electricite-octobre.pdf' }).click();
    await expect(page.getByRole('heading', { name: 'Informations de la facture' })).toBeVisible();
    const adresse = new URL(page.url()).pathname;

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await page.goto(adresse);
    await expect(page).toHaveURL(/\/connexion/);
    const sansSession = await page.request.get(`${adresseApi(page)}${adresse}/fichier`);
    expect(sansSession.status()).toBe(401);

    await nouveauCabinet(page, 'Autre Cabinet');
    await page.goto(adresse);
    await expect(page.getByRole('alert')).toHaveText('Facture introuvable ou accès non autorisé');
    await expect(page.locator('img, iframe')).toHaveCount(0);
  });
});

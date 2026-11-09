import { Page, expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-08 Activer son compte, de l'écran jusqu'à la base. Aucune API d'envoi d'emails n'étant
// branchée, le backend local (mode test) affiche le lien d'invitation à l'écran.
const MOT_DE_PASSE = 'Sophie#Pointeo2026';

async function inviterLeDirigeant(page: Page, email: string): Promise<string> {
  await page.getByRole('button', { name: 'Inviter le dirigeant' }).click();
  await page.getByLabel('Prénom').fill('Sophie');
  await page.getByLabel('Nom', { exact: true }).fill('Martin');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: "Envoyer l'invitation" }).click();
  return lienAffiche(page);
}

async function lienAffiche(page: Page): Promise<string> {
  const lien = await page.getByRole('link', { name: "Mode test : lien d'invitation" }).getAttribute('href');
  expect(lien).toMatch(/^\/activer-compte\?jeton=.+/);
  return lien!;
}

async function choisirMotDePasse(page: Page): Promise<void> {
  await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
  await page.getByLabel('Confirmer le mot de passe').fill(MOT_DE_PASSE);
}

test.describe('US-08 Activer son compte', () => {
  test('Parcours complet — dossier créé, retrouvé, ouvert, dirigeant invité, activé, espace client', async ({
    page,
    context,
  }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Boulangerie Leroy SARL', '812345678');
    await expect(page).toHaveURL(/\/dossiers\/\d+$/);
    // Retrouvé dans la liste, puis ouvert.
    await page.getByRole('link', { name: '← Dossiers' }).click();
    await expect(page).toHaveURL(/\/dossiers$/);
    await page.getByRole('link', { name: 'Boulangerie Leroy SARL' }).click();
    await expect(page.getByRole('heading', { name: 'Boulangerie Leroy SARL' })).toBeVisible();

    const email = `sophie.martin+${Date.now()}@boulangerie-test.fr`;
    const lien = await inviterLeDirigeant(page, email);
    await expect(page.getByText('Espace client : Invité')).toBeVisible();

    // Le dirigeant ouvre le lien reçu, dans son propre onglet.
    const dirigeant = await context.newPage();
    await dirigeant.goto(lien);
    await expect(dirigeant.getByRole('heading', { name: 'Activer votre espace' })).toBeVisible();
    await expect(dirigeant.getByText('Bienvenue Sophie Martin')).toBeVisible();
    // RG2 : pas de numéro de mobile.
    await expect(dirigeant.getByLabel(/mobile/i)).toHaveCount(0);

    // RG2 : CGU obligatoires.
    await choisirMotDePasse(dirigeant);
    await dirigeant.getByRole('button', { name: 'Activer mon espace' }).click();
    await expect(dirigeant.getByText("Vous devez accepter les conditions d'utilisation")).toBeVisible();

    // RG3 : actif aussitôt, sans code ; redirigé vers l'espace client.
    await dirigeant.getByLabel(/J'accepte les/).check();
    await dirigeant.getByRole('button', { name: 'Activer mon espace' }).click();
    await expect(dirigeant).toHaveURL(/\/espace-client$/);
    await expect(dirigeant.getByRole('heading', { name: 'Bonjour Sophie Martin' })).toBeVisible();

    // RG1 : une seule fois.
    await dirigeant.goto(lien);
    await expect(dirigeant.getByRole('alert')).toContainText('Votre espace est déjà activé');
    await dirigeant.getByRole('link', { name: 'Se connecter' }).click();
    await expect(dirigeant).toHaveURL(/\/connexion$/);

    // Côté cabinet : l'espace client est actif.
    await page.reload();
    await expect(page.getByText('Espace client : Actif')).toBeVisible();
  });

  test('RG1 — invitation expirée : l’ancien lien, annulé par un renvoi, ne fonctionne plus', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Garage Morel SAS', '812345679');
    const ancien = await inviterLeDirigeant(page, `paul.morel+${Date.now()}@garage-test.fr`);
    await page.getByRole('button', { name: "Renvoyer l'invitation" }).click();
    await expect(page.getByRole('status')).toContainText('Une nouvelle invitation a été envoyée');

    await page.goto(ancien);
    await expect(page.getByRole('alert')).toHaveText(
      'Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer.',
    );
    await expect(page.getByLabel('Mot de passe', { exact: true })).toHaveCount(0);
  });

  test('Collaborateur invité par l’administrateur (US-19) — arrive sur la liste des dossiers', async ({
    page,
    context,
  }) => {
    const siren = await nouveauCabinet(page);
    await page.goto('/equipe');
    await page.getByLabel('Prénom').fill('Lucie');
    await page.getByLabel('Nom', { exact: true }).fill('Moreau');
    await page.getByLabel('Email professionnel').fill(`lucie.moreau+${siren}@cabinet-test.fr`);
    await page.getByLabel('Rôle').selectOption({ label: 'Comptable' });
    await page.getByRole('button', { name: "Envoyer l'invitation" }).click();
    const lien = await lienAffiche(page);

    const lucie = await context.newPage();
    await lucie.goto(lien);
    await choisirMotDePasse(lucie);
    await lucie.getByLabel(/J'accepte les/).check();
    await lucie.getByRole('button', { name: 'Activer mon espace' }).click();
    await expect(lucie).toHaveURL(/\/dossiers$/);

    await page.reload();
    await expect(page.getByRole('row', { name: /Lucie Moreau/ })).not.toContainText('Invitée');
  });
});

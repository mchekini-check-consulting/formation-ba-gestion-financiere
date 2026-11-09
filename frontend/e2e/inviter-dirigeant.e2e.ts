import { expect, test } from '@playwright/test';
import { creerDossier, nouveauCabinet } from './outils';

// US-14 Inviter le dirigeant, de l'écran jusqu'à la base. Aucune API d'envoi d'emails n'étant
// branchée, le backend local (mode test) renvoie le lien d'invitation à l'écran.
test.describe('US-14 Inviter le dirigeant', () => {
  test('RG1, RG2, RG3 — invitation, état « Invité », renvoi avec un nouveau lien', async ({ page }) => {
    await nouveauCabinet(page);
    await creerDossier(page, 'Boulangerie Leroy SARL', '812345678');
    await expect(page.getByText('Espace client : Non invité')).toBeVisible();

    await page.getByRole('button', { name: 'Inviter le dirigeant' }).click();
    // CA erreur.
    await page.getByLabel('Prénom').fill('Sophie');
    await page.getByLabel('Nom', { exact: true }).fill('Martin');
    await page.getByLabel('Email').fill('sophie@');
    await page.getByRole('button', { name: "Envoyer l'invitation" }).click();
    await expect(page.getByText('Saisissez une adresse email valide')).toBeVisible();

    const email = `sophie.martin+${Date.now()}@boulangerie-test.fr`;
    await page.getByLabel('Email').fill(email);
    await page.getByRole('button', { name: "Envoyer l'invitation" }).click();
    await expect(page.getByRole('status')).toContainText(`Invitation envoyée à ${email}`);
    // RG3 : Non invité -> Invité.
    await expect(page.getByText('Espace client : Invité')).toBeVisible();
    const premierLien = await page.getByRole('link', { name: "Mode test : lien d'invitation" }).getAttribute('href');
    expect(premierLien).toMatch(/^\/activer-compte\?jeton=.+/);

    // RG2 : nouveau lien, l'ancien est annulé.
    await page.getByRole('button', { name: "Renvoyer l'invitation" }).click();
    await expect(page.getByRole('status')).toContainText('Une nouvelle invitation a été envoyée');
    const secondLien = await page.getByRole('link', { name: "Mode test : lien d'invitation" }).getAttribute('href');
    expect(secondLien).toMatch(/^\/activer-compte\?jeton=.+/);
    expect(secondLien).not.toBe(premierLien);

    // L'état est enregistré : toujours « Invité » après rechargement et dans la liste.
    await page.reload();
    await expect(page.getByText('Espace client : Invité')).toBeVisible();
    await page.getByRole('link', { name: '← Dossiers' }).click();
    await expect(page.getByRole('row', { name: /Boulangerie Leroy SARL/ })).toContainText('Invité');
  });
});

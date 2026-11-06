import { Page, expect, test } from '@playwright/test';
import { JEAN, SOPHIE, codeRecu, remettreAZero, seConnecter } from './outils';

// US-19 Inviter un collaborateur avec un rôle (PTO-26), de l'écran jusqu'à la base.
// Chaque test crée son propre cabinet (US-36) : Claire en est l'administratrice, connectée.
async function claireAdministratrice(page: Page): Promise<string> {
  const siren = String(100_000_000 + Math.floor(Math.random() * 900_000_000));
  await page.goto('/creer-compte');
  await page.getByLabel('Nom du cabinet').fill('Cabinet Test');
  await page.getByLabel('SIREN').fill(siren);
  await page.getByLabel('Prénom').fill('Claire');
  await page.getByLabel('Nom', { exact: true }).fill('Martin');
  await page.getByLabel('Email professionnel').fill(`claire.martin+${siren}@cabinet-test.fr`);
  await page.getByLabel('Mobile').fill('06 12 34 56 78');
  await page.getByLabel('Mot de passe', { exact: true }).fill('Pointeo2026!!');
  await page.getByLabel('Nombre de dossiers prévus').fill('120');
  await page.getByLabel(/J'accepte les/).check();
  await page.getByRole('button', { name: 'Recevoir le code SMS' }).click();
  await page.getByLabel('Code reçu par SMS').fill(await codeRecu(page));
  await page.getByRole('button', { name: 'Valider et créer mon cabinet' }).click();
  await expect(page.getByRole('heading', { name: 'Cabinet Test est prêt' })).toBeVisible();
  return siren;
}

async function inviter(page: Page, email: string, role: string): Promise<void> {
  await page.getByLabel('Prénom').fill('Lucie');
  await page.getByLabel('Nom', { exact: true }).fill('Moreau');
  await page.getByLabel('Email professionnel').fill(email);
  await page.getByLabel('Rôle').selectOption({ label: role });
  await page.getByRole('button', { name: "Envoyer l'invitation" }).click();
}

test.describe('US-19 Inviter un collaborateur avec un rôle', () => {
  test('CA1 — Claire invite Lucie Moreau comme Comptable', async ({ page }) => {
    const siren = await claireAdministratrice(page);
    await page.getByRole('link', { name: 'Inviter mes collaborateurs' }).click();
    await expect(page.getByRole('heading', { name: 'Équipe du cabinet' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Équipe' })).toBeVisible();

    const email = `lucie.moreau+${siren}@cabinet-test.fr`;
    await inviter(page, email, 'Comptable');
    await expect(page.getByRole('status')).toContainText(`Invitation envoyée à ${email}`);
    const lucie = page.getByRole('row', { name: /Lucie Moreau/ });
    await expect(lucie).toContainText('Comptable');
    await expect(lucie).toContainText('Invitée');

    // Toujours là après rechargement : l'invitation est enregistrée en base.
    await page.reload();
    await expect(page.getByRole('row', { name: /Lucie Moreau/ })).toContainText('Invitée');
  });

  test('CA2 — une adresse déjà utilisée est refusée', async ({ page }) => {
    await claireAdministratrice(page);
    await page.goto('/equipe');
    await inviter(page, SOPHIE, 'Associé');
    await expect(page.getByRole('alert')).toHaveText('Cette adresse est déjà utilisée');
    await expect(page.getByRole('row', { name: /Lucie Moreau/ })).toHaveCount(0);
  });

  test('RG1 — un collaborateur non administrateur n’a pas accès à l’équipe', async ({ page, request }) => {
    await remettreAZero(request, JEAN);
    await seConnecter(page, JEAN);
    await expect(page).toHaveURL(/\/dossiers$/);
    await expect(page.getByRole('link', { name: 'Équipe' })).toHaveCount(0);

    await page.goto('/equipe');
    await expect(page).toHaveURL(/\/dossiers$/);
  });
});

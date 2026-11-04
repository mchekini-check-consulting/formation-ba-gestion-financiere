import { expect, test } from '@playwright/test';
import { MOT_DE_PASSE, SOPHIE, remettreAZero, saisirIdentifiants, seConnecter } from './outils';

// US-05 Bloquer la connexion après 3 échecs (PTO-12) et US-06 Réinitialiser son mot de passe (PTO-13).
// Les tests s'enchaînent sur le compte de Sophie : bloqué par US-05, débloqué par US-06.
test.describe.configure({ mode: 'serial' });

const NOUVEAU = 'Nouveau#Pointeo2026';

test.beforeAll(async ({ request }) => remettreAZero(request, SOPHIE));
test.afterAll(async ({ request }) => remettreAZero(request, SOPHIE));

test('US-05 CA1 — 3e mauvais mot de passe -> « Compte bloqué 15 minutes »', async ({ page }) => {
  await page.goto('/connexion');
  for (const essai of ['mauvais-1', 'mauvais-2']) {
    await saisirIdentifiants(page, SOPHIE, essai);
    await expect(page.getByRole('alert')).toHaveText('Email ou mot de passe incorrect');
  }
  await saisirIdentifiants(page, SOPHIE, 'mauvais-3');
  await expect(page.getByRole('alert')).toContainText('Compte bloqué 15 minutes');

  // Pendant le blocage, même le bon mot de passe est refusé.
  await saisirIdentifiants(page, SOPHIE, MOT_DE_PASSE);
  await expect(page.getByRole('alert')).toContainText('Compte bloqué 15 minutes');
});

test('US-06 RG2 — même message que le compte existe ou non', async ({ page }) => {
  await page.goto('/mot-de-passe-oublie');
  await page.getByLabel('Email').fill('personne@cabinet-test.fr');
  await page.getByRole('button', { name: 'Envoyer le lien' }).click();
  await expect(page.getByRole('status')).toHaveText('Si un compte existe, un email vous a été envoyé');
});

test('US-06 CA2 — lien expiré ou inconnu -> « Ce lien a expiré, demandez-en un nouveau »', async ({ page }) => {
  await page.goto('/reinitialiser-mot-de-passe?jeton=lien-perime');
  await expect(page.getByRole('alert')).toContainText('Ce lien a expiré, demandez-en un nouveau');
  await page.getByRole('link', { name: 'Faire une nouvelle demande' }).click();
  await expect(page).toHaveURL(/\/mot-de-passe-oublie$/);
});

test('US-06 CA3 puis CA1 — mot de passe trop faible refusé, puis changé : Sophie peut se connecter', async ({
  page,
}) => {
  await page.goto('/connexion');
  await page.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
  await page.getByLabel('Email').fill(SOPHIE);
  await page.getByRole('button', { name: 'Envoyer le lien' }).click();
  await expect(page.getByRole('status')).toHaveText('Si un compte existe, un email vous a été envoyé');
  await page.getByRole('link', { name: 'ouvrir le lien reçu par email' }).click();
  await expect(page.getByRole('heading', { name: 'Nouveau mot de passe' })).toBeVisible();

  // CA3 : « bonjour »
  await page.getByLabel('Nouveau mot de passe').fill('bonjour');
  await page.getByLabel('Confirmer le mot de passe').fill('bonjour');
  const regles = page.getByRole('list', { name: 'Règles du mot de passe' });
  await expect(regles).toContainText('✗ 12 caractères minimum');
  await expect(regles).toContainText('✗ Une majuscule');
  await expect(regles).toContainText('✗ Un chiffre');
  await expect(regles).toContainText('✗ Un caractère spécial');
  await expect(page.getByRole('button', { name: 'Valider' })).toBeDisabled();

  // CA1 : mot de passe valide
  await page.getByLabel('Nouveau mot de passe').fill(NOUVEAU);
  await page.getByLabel('Confirmer le mot de passe').fill(NOUVEAU);
  await expect(regles).not.toContainText('✗');
  await page.getByRole('button', { name: 'Valider' }).click();
  await expect(page).toHaveURL(/\/connexion\?raison=mot-de-passe-change$/);
  await expect(page.getByRole('status')).toContainText('Votre mot de passe a été changé');

  // Le compte bloqué par US-05 est débloqué, l'ancien mot de passe ne marche plus.
  await saisirIdentifiants(page, SOPHIE, MOT_DE_PASSE);
  await expect(page.getByRole('alert')).toHaveText('Email ou mot de passe incorrect');
  await seConnecter(page, SOPHIE, NOUVEAU);
  await expect(page).toHaveURL(/\/espace-client$/);
});

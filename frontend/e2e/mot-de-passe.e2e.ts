import { expect, test } from '@playwright/test';
import {
  EN_LIGNE,
  MOT_DE_PASSE_CABINET,
  emailRecuApres,
  lienDansEmail,
  nouveauCabinet,
  saisirIdentifiants,
  seConnecter,
} from './outils';

// US-05 Bloquer la connexion après 3 échecs (PTO-12) et US-06 Réinitialiser son mot de passe
// (PTO-13). Compte propre au test (nouveau cabinet) ; le lien arrive par email (Mailpit).
const NOUVEAU = 'Nouveau#Pointeo2026';

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

test('US-05 puis US-06 — compte bloqué après 3 échecs, débloqué par le lien reçu par email', async ({ page }) => {
  // Le lien arrive dans Mailpit : celui d'un environnement en ligne demande une connexion (SSO).
  test.skip(EN_LIGNE, 'Mailpit de l’environnement en ligne non lisible par les tests');
  const email = await nouveauCabinet(page);
  await page.getByRole('button', { name: 'Se déconnecter' }).click();

  // US-05 CA1 : 3e mauvais mot de passe -> bloqué, même avec le bon.
  await page.goto('/connexion');
  for (const essai of ['mauvais-1', 'mauvais-2']) {
    await saisirIdentifiants(page, email, essai);
    await expect(page.getByRole('alert')).toHaveText('Email ou mot de passe incorrect');
  }
  await saisirIdentifiants(page, email, 'mauvais-3');
  await expect(page.getByRole('alert')).toContainText('Compte bloqué 15 minutes');
  await saisirIdentifiants(page, email, MOT_DE_PASSE_CABINET);
  await expect(page.getByRole('alert')).toContainText('Compte bloqué 15 minutes');

  // US-06 : demande du lien, reçu par email.
  await page.goto('/mot-de-passe-oublie');
  await page.getByLabel('Email').fill(email);
  const texte = await emailRecuApres(page.request, email, async () => {
    await page.getByRole('button', { name: 'Envoyer le lien' }).click();
    await expect(page.getByRole('status')).toHaveText('Si un compte existe, un email vous a été envoyé');
  });
  await page.goto(lienDansEmail(texte, 'reinitialiser-mot-de-passe'));
  await expect(page.getByRole('heading', { name: 'Nouveau mot de passe' })).toBeVisible();

  // CA3 : mot de passe trop faible.
  await page.getByLabel('Nouveau mot de passe').fill('bonjour');
  await page.getByLabel('Confirmer le mot de passe').fill('bonjour');
  const regles = page.getByRole('list', { name: 'Règles du mot de passe' });
  await expect(regles).toContainText('✗ 12 caractères minimum');
  await expect(page.getByRole('button', { name: 'Valider' })).toBeDisabled();

  // CA1 : mot de passe valide.
  await page.getByLabel('Nouveau mot de passe').fill(NOUVEAU);
  await page.getByLabel('Confirmer le mot de passe').fill(NOUVEAU);
  await expect(regles).not.toContainText('✗');
  await page.getByRole('button', { name: 'Valider' }).click();
  await expect(page).toHaveURL(/\/connexion\?raison=mot-de-passe-change$/);
  await expect(page.getByRole('status')).toContainText('Votre mot de passe a été changé');

  // Débloqué ; l'ancien mot de passe ne marche plus.
  await saisirIdentifiants(page, email, MOT_DE_PASSE_CABINET);
  await expect(page.getByRole('alert')).toHaveText('Email ou mot de passe incorrect');
  await seConnecter(page, email, NOUVEAU);
  await expect(page).toHaveURL(/\/dossiers$/);
});

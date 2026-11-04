import { expect, test } from '@playwright/test';
import { JEAN, SOPHIE, remettreAZero, seConnecter } from './outils';

// US-07 Terminer sa session : déconnexion et expiration (PTO-14).
test.beforeAll(async ({ request }) => {
  await remettreAZero(request, JEAN);
  await remettreAZero(request, SOPHIE);
});

test('CA1 — « Se déconnecter » ramène à l\'écran de connexion et ferme la session', async ({ page }) => {
  await seConnecter(page, JEAN);
  await expect(page.getByRole('heading', { name: 'Dossiers clients' })).toBeVisible();
  await expect(page.getByTestId('nom-utilisateur')).toHaveText('Jean Dupont');

  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page).toHaveURL(/\/connexion\?raison=deconnexion$/);
  await expect(page.getByRole('status')).toHaveText('Vous êtes déconnecté');

  // La session est bien fermée : les pages connectées ne sont plus accessibles.
  await page.goto('/dossiers');
  await expect(page).toHaveURL(/\/connexion$/);
});

test('RG2 puis RG3 — avertissement 2 minutes avant, puis « Votre session a expiré »', async ({ page }) => {
  await page.clock.install();
  await seConnecter(page, JEAN);
  await expect(page.getByRole('heading', { name: 'Dossiers clients' })).toBeVisible();

  await page.clock.fastForward('28:00');
  await expect(page.getByRole('dialog')).toContainText('Votre session va expirer dans');
  await page.clock.fastForward('02:00');

  await expect(page).toHaveURL(/\/connexion\?raison=session-expiree$/);
  await expect(page.getByRole('status')).toHaveText('Votre session a expiré, reconnectez-vous');
});

test('RG2 — « Rester connecté » garde la session ouverte', async ({ page }) => {
  await page.clock.install();
  await seConnecter(page, JEAN);
  await expect(page.getByRole('heading', { name: 'Dossiers clients' })).toBeVisible();
  await page.clock.fastForward('28:30');
  await page.getByRole('button', { name: 'Rester connecté' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.getByRole('link', { name: /Boulangerie Leroy SARL/ }).click();
  await expect(page.getByRole('heading', { name: 'Boulangerie Leroy SARL' })).toBeVisible();
});

test('pages réservées : sans connexion ou avec le mauvais rôle', async ({ page }) => {
  await page.goto('/dossiers');
  await expect(page).toHaveURL(/\/connexion$/);

  await seConnecter(page, SOPHIE);
  await expect(page).toHaveURL(/\/espace-client$/);
  await page.goto('/dossiers');
  await expect(page).toHaveURL(/\/espace-client$/);
});

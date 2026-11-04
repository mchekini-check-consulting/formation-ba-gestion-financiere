import { expect, test } from '@playwright/test';
import { JEAN, MOT_DE_PASSE, SOPHIE, codeRecu, remettreAZero, saisirIdentifiants, taperCode } from './outils';

// US-04 Valider la connexion par un code reçu par SMS (PTO-5).
test.describe('US-04 Code SMS', () => {
  test.beforeAll(async ({ request }) => {
    await remettreAZero(request, JEAN);
    await remettreAZero(request, SOPHIE);
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/connexion');
    await saisirIdentifiants(page, JEAN, MOT_DE_PASSE);
    await expect(page.getByRole('heading', { name: 'Vérifiez votre identité' })).toBeVisible();
  });

  test('CA1 — bon code dans les 5 minutes -> liste des dossiers', async ({ page }) => {
    await expect(page.getByTestId('telephone')).toHaveText('06 •• •• •• 42');
    await taperCode(page, await codeRecu(page));
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page).toHaveURL(/\/dossiers$/);
    await expect(page.getByRole('heading', { name: 'Dossiers clients' })).toBeVisible();
  });

  test('CA2 — code faux -> « Code incorrect. Il vous reste 2 tentatives. »', async ({ page }) => {
    const faux = (await codeRecu(page)) === '111111' ? '222222' : '111111';
    await taperCode(page, faux);
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByRole('alert')).toHaveText('Code incorrect. Il vous reste 2 tentatives.');
  });

  test('RG3 — après 3 codes faux, retour à l\'écran de connexion', async ({ page }) => {
    const faux = (await codeRecu(page)) === '111111' ? '222222' : '111111';
    for (const restantes of ['Il vous reste 2 tentatives.', 'Il vous reste 1 tentative.']) {
      await taperCode(page, faux);
      await page.getByRole('button', { name: 'Valider' }).click();
      await expect(page.getByRole('alert')).toHaveText(`Code incorrect. ${restantes}`);
      await expect(page.getByLabel('Chiffre 1')).toHaveValue('');
    }
    await taperCode(page, faux);
    await page.getByRole('button', { name: 'Valider' }).click();
    await expect(page).toHaveURL(/\/connexion$/);
    await expect(page.getByRole('alert')).toHaveText('Trop de codes incorrects. Reconnectez-vous.');
  });

  test('RG2 — « Renvoyer un code » après 60 secondes : le nouveau code remplace l\'ancien', async ({ page }) => {
    // Le délai de 60 s est vérifié par le serveur, avec sa propre horloge : on attend vraiment.
    test.setTimeout(120_000);
    await expect(page.getByText('Renvoyer un code dans')).toBeVisible();
    await page.waitForTimeout(61_000);
    await page.getByRole('button', { name: 'Renvoyer un code' }).click();
    await expect(page.getByRole('status')).toHaveText('Un nouveau code vous a été envoyé.');

    await taperCode(page, await codeRecu(page));
    await page.getByRole('button', { name: 'Valider' }).click();
    await expect(page).toHaveURL(/\/dossiers$/);
  });
});

test('US-03 RG3 — le dirigeant arrive sur son espace client', async ({ page }) => {
  await page.goto('/connexion');
  await saisirIdentifiants(page, SOPHIE, MOT_DE_PASSE);
  await taperCode(page, await codeRecu(page));
  await page.getByRole('button', { name: 'Valider' }).click();

  await expect(page).toHaveURL(/\/espace-client$/);
  await expect(page.getByRole('heading', { name: 'Bonjour Sophie Martin' })).toBeVisible();
});

import { APIRequestContext, Page, expect } from '@playwright/test';

// Comptes de démonstration (backend : V6__compte_donnees_demo.sql, V7__authentification.sql).
export const JEAN = 'jean.dupont@cabinet-test.fr';
export const SOPHIE = 'sophie.martin@boulangerie-test.fr';
export const MOT_DE_PASSE = 'Pointeo2026!';

/**
 * Remet un compte de démonstration dans un état connu (mot de passe d'origine, compte
 * débloqué, sessions fermées) par « Mot de passe oublié » : le backend tourne en mode
 * test, il renvoie le jeton du lien au lieu de l'envoyer par email.
 */
export async function remettreAZero(request: APIRequestContext, email: string): Promise<void> {
  const demande = await request.post('/api/auth/mot-de-passe-oublie', { data: { email } });
  const { jetonDeTest } = (await demande.json()) as { jetonDeTest: string | null };
  expect(jetonDeTest, 'backend lancé en mode test (./mvnw spring-boot:run)').toBeTruthy();
  const changement = await request.post('/api/auth/reinitialisation', {
    data: { jeton: jetonDeTest, motDePasse: MOT_DE_PASSE },
  });
  expect(changement.status()).toBe(204);
}

export async function saisirIdentifiants(page: Page, email: string, motDePasse: string): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(motDePasse);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

/** Code affiché par le mode test (aucune API d'envoi d'emails branchée). */
export async function codeRecu(page: Page): Promise<string> {
  return (await page.getByTestId('code-de-test').locator('strong').textContent()) ?? '';
}

export async function taperCode(page: Page, code: string): Promise<void> {
  for (const [i, chiffre] of [...code].entries()) {
    await page.getByLabel(`Chiffre ${i + 1}`).fill(chiffre);
  }
}

/**
 * Crée un nouveau cabinet (US-36) dont Claire est l'administratrice, connectée.
 * Renvoie le SIREN du cabinet (unique : chaque test a ses propres dossiers).
 */
export async function nouveauCabinet(page: Page, nomCabinet = 'Cabinet Test'): Promise<string> {
  const siren = String(100_000_000 + Math.floor(Math.random() * 900_000_000));
  await page.goto('/creer-compte');
  await page.getByLabel('Nom du cabinet').fill(nomCabinet);
  await page.getByLabel('SIREN').fill(siren);
  await page.getByLabel('Prénom').fill('Claire');
  await page.getByLabel('Nom', { exact: true }).fill('Martin');
  await page.getByLabel('Email professionnel').fill(`claire.martin+${siren}@cabinet-test.fr`);
  await page.getByLabel('Mot de passe', { exact: true }).fill('Pointeo2026!!');
  await page.getByLabel('Nombre de dossiers prévus').fill('120');
  await page.getByLabel(/J'accepte les/).check();
  await page.getByRole('button', { name: 'Recevoir le code par email' }).click();
  await page.getByLabel('Code reçu par email').fill(await codeRecu(page));
  await page.getByRole('button', { name: 'Valider et créer mon cabinet' }).click();
  await expect(page.getByRole('heading', { name: `${nomCabinet} est prêt` })).toBeVisible();
  return siren;
}

/** US-10 : remplit et envoie le formulaire « Créer un dossier ». */
export async function creerDossier(page: Page, raisonSociale: string, siren: string): Promise<void> {
  await page.goto('/dossiers/nouveau');
  await page.getByLabel('Raison sociale').fill(raisonSociale);
  await page.getByLabel('SIREN').fill(siren);
  await page.getByRole('button', { name: 'Créer', exact: true }).click();
}

/** Connexion complète : email + mot de passe, puis code reçu par email. */
export async function seConnecter(page: Page, email: string, motDePasse = MOT_DE_PASSE): Promise<void> {
  await page.goto('/connexion');
  await saisirIdentifiants(page, email, motDePasse);
  await expect(page).toHaveURL(/\/verification-code$/);
  await taperCode(page, await codeRecu(page));
  await page.getByRole('button', { name: 'Valider' }).click();
}

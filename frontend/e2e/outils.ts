import { APIRequestContext, Page, expect } from '@playwright/test';
import { resolve } from 'node:path';

// Comptes de démonstration (backend : migrations V6 et V25), mot de passe commun.
export const JEAN = 'jean.dupont@cabinet-test.fr';
export const SOPHIE = 'sophie.martin@boulangerie-test.fr';
export const MOT_DE_PASSE = 'Pointeo2026!';

/** Mot de passe des cabinets créés par les tests (nouveauCabinet). */
export const MOT_DE_PASSE_CABINET = 'Pointeo2026!!';

/** Factures d'exemple du backend (lues sans Tesseract : PDF avec texte). */
export const FACTURE_EDF = resolve('..', 'backend', 'src', 'test', 'resources', 'exemples', 'facture-electricite-octobre.pdf');

// Mailpit (backend/compose.yaml) : les codes et liens partent uniquement par email,
// le backend n'en affiche jamais à l'écran.
const MAILPIT = process.env['MAILPIT_URL'] ?? 'http://localhost:8025';

interface ResumeEmail {
  ID: string;
}

async function derniersEmails(request: APIRequestContext, destinataire: string): Promise<ResumeEmail[]> {
  const reponse = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${destinataire}"` } });
  expect(reponse.ok(), `Mailpit joignable sur ${MAILPIT}`).toBeTruthy();
  return ((await reponse.json()) as { messages: ResumeEmail[] }).messages;
}

/**
 * Lance une action qui envoie un email puis renvoie le texte du nouvel email reçu par ce
 * destinataire (attend son arrivée dans Mailpit).
 */
export async function emailRecuApres(
  request: APIRequestContext,
  destinataire: string,
  action: () => Promise<unknown>,
): Promise<string> {
  const avant = new Set((await derniersEmails(request, destinataire)).map((m) => m.ID));
  await action();
  let nouveau: ResumeEmail | undefined;
  await expect
    .poll(async () => {
      nouveau = (await derniersEmails(request, destinataire)).find((m) => !avant.has(m.ID));
      return nouveau !== undefined;
    }, { message: `email reçu par ${destinataire}`, timeout: 15_000 })
    .toBe(true);
  const message = await request.get(`${MAILPIT}/api/v1/message/${nouveau!.ID}`);
  return ((await message.json()) as { Text: string }).Text;
}

/** Lien (…/chemin?jeton=…) contenu dans un email, réduit à son chemin pour le serveur de test. */
export function lienDansEmail(texte: string, chemin: string): string {
  const lien = new RegExp(`/${chemin}\\?jeton=\\S+`).exec(texte)?.[0];
  expect(lien, `lien /${chemin} dans l'email`).toBeTruthy();
  return lien!;
}

/** Tests lancés sur un environnement en ligne (E2E_URL) plutôt que sur le poste. */
export const EN_LIGNE = !!process.env['E2E_URL'];

/**
 * Adresse du backend vue par le navigateur, calculée depuis celle de la page comme apiBase()
 * (src/app/api.ts) : relais /api en local, gateway en ligne.
 */
export function adresseApi(page: Page): string {
  const { origin, hostname } = new URL(page.url());
  const [, projet, env, ...domaine] = hostname.split('.');
  if (!projet || !env || domaine.length === 0) {
    return `${origin}/api`;
  }
  return `https://gateway.${projet}.${env}.${domaine.join('.')}/backend`;
}

/** Envoie le formulaire de connexion et attend la réponse du backend (l'écran vide alors le mot de passe). */
export async function saisirIdentifiants(page: Page, email: string, motDePasse: string): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(motDePasse);
  const reponse = page.waitForResponse((r) => r.url().endsWith('/auth/connexion') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await reponse;
}

/** Connexion complète (email + mot de passe) : l'écran d'accueil du rôle s'ouvre. */
export async function seConnecter(page: Page, email: string, motDePasse = MOT_DE_PASSE): Promise<void> {
  await page.goto('/connexion');
  await saisirIdentifiants(page, email, motDePasse);
  await expect(page).not.toHaveURL(/\/connexion/);
}

/**
 * Crée un nouveau cabinet (US-36) dont Claire est l'administratrice, connectée sur ses dossiers.
 * Renvoie son email (unique : chaque test a son propre cabinet et ses propres dossiers).
 */
export async function nouveauCabinet(page: Page, nomCabinet = 'Cabinet Test'): Promise<string> {
  const siren = String(100_000_000 + Math.floor(Math.random() * 900_000_000));
  const email = `claire.martin+${siren}@cabinet-test.fr`;
  await page.goto('/creer-compte');
  await page.getByLabel('Nom du cabinet').fill(nomCabinet);
  await page.getByLabel('SIREN').fill(siren);
  await page.getByLabel('Prénom').fill('Claire');
  await page.getByLabel('Nom', { exact: true }).fill('Martin');
  await page.getByLabel('Email professionnel').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE_CABINET);
  await page.getByLabel('Nombre de dossiers prévus').fill('120');
  await page.getByLabel(/J'accepte les/).check();
  await page.getByRole('button', { name: 'Créer mon cabinet' }).click();
  await expect(page.getByRole('heading', { name: `${nomCabinet} est prêt` })).toBeVisible();
  await page.getByRole('link', { name: 'Accéder à mon cabinet' }).click();
  await expect(page).toHaveURL(/\/dossiers$/);
  return email;
}

/** US-10 : remplit et envoie le formulaire « Créer un dossier ». */
export async function creerDossier(page: Page, raisonSociale: string, siren: string): Promise<void> {
  await page.goto('/dossiers/nouveau');
  await page.getByLabel('Raison sociale').fill(raisonSociale);
  await page.getByLabel('SIREN').fill(siren);
  await page.getByRole('button', { name: 'Créer', exact: true }).click();
}

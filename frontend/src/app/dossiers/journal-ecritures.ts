import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import {
  EcritureJournal,
  EcritureService,
  JournalEcritures,
  LIBELLES_JOURNAUX,
  euros,
  filtrerEcritures,
  lireMontant,
  messageDesequilibre,
  saisieMontant,
} from './ecriture.service';
import { dateOuTiret, formaterDepot } from './facture.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
const MESSAGE_INTROUVABLE = 'Écriture introuvable : rechargez le journal.';
export const MESSAGE_MONTANT = 'Chaque ligne a un seul montant, au débit ou au crédit, à 2 décimales au plus.';

/** Ligne du formulaire « Modifier l'écriture » (RG2 : compte, débit, crédit). */
interface LigneSaisie {
  numeroCompte: string;
  debit: string;
  credit: string;
}

/**
 * US-47 Onglet « Écritures » du dossier : journal d'une période (RG1), correction d'une écriture non
 * exportée (RG2) refusée si elle est déséquilibrée ou hors plan (RG3), auteur et date gardés (RG4) ;
 * une écriture exportée s'ouvre en lecture seule (CA3).
 */
@Component({
  selector: 'app-journal-ecritures',
  templateUrl: './journal-ecritures.html',
  styleUrl: './journal-ecritures.scss',
})
export class JournalEcrituresComponent implements OnInit {
  private readonly service = inject(EcritureService);
  readonly dossierId = input.required<number>();

  protected readonly journal = signal<JournalEcritures | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly filtreJournal = signal('');
  protected readonly recherche = signal('');
  protected readonly ecritures = computed(() =>
    filtrerEcritures(this.journal()?.ecritures ?? [], this.filtreJournal(), this.recherche()),
  );
  protected readonly journaux = computed(() =>
    [...new Set((this.journal()?.ecritures ?? []).map((e) => e.journal))].map((code) => ({
      code,
      libelle: LIBELLES_JOURNAUX[code] ?? code,
    })),
  );
  protected readonly libellePeriode = computed(() => {
    const j = this.journal();
    return j?.periodes.find((p) => p.code === j.periode)?.libelle ?? '';
  });

  // Formulaire « Modifier l'écriture ».
  protected readonly ouverte = signal<EcritureJournal | null>(null);
  protected readonly libelle = signal('');
  protected readonly lignes = signal<LigneSaisie[]>([]);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurFormulaire = signal<string | null>(null);
  protected readonly enregistree = signal(false);
  protected readonly messageMontant = MESSAGE_MONTANT;

  private readonly montants = computed(() =>
    this.lignes().map((l) => ({ debit: lireMontant(l.debit), credit: lireMontant(l.credit) })),
  );
  protected readonly montantsInvalides = computed(() =>
    this.montants().some((m) => m.debit === null || m.credit === null || (m.debit === 0) === (m.credit === 0)),
  );
  protected readonly totalDebit = computed(() => this.montants().reduce((t, m) => t + (m.debit ?? 0), 0));
  protected readonly totalCredit = computed(() => this.montants().reduce((t, m) => t + (m.credit ?? 0), 0));
  /** RG3, CA2 : écart affiché tant que débit ≠ crédit ; « Enregistrer » reste désactivé. */
  protected readonly desequilibre = computed(() =>
    this.montantsInvalides()
      ? null
      : messageDesequilibre(
          this.montants().map((m) => m.debit ?? 0),
          this.montants().map((m) => m.credit ?? 0),
        ),
  );
  protected readonly enregistrable = computed(
    () =>
      !this.envoiEnCours() &&
      this.libelle().trim() !== '' &&
      this.lignes().every((l) => l.numeroCompte.trim() !== '') &&
      !this.montantsInvalides() &&
      this.desequilibre() === null,
  );

  ngOnInit(): void {
    this.charger(null);
  }

  /** RG1 : journal de la période choisie (le dernier mois par défaut). */
  charger(periode: string | null): void {
    this.erreur.set(null);
    this.service.consulter(this.dossierId(), periode).subscribe({
      next: (journal) => {
        this.journal.set(journal);
        this.fermer();
      },
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  ouvrir(ecriture: EcritureJournal): void {
    this.ouverte.set(ecriture);
    this.libelle.set(ecriture.libelle);
    this.lignes.set(
      ecriture.lignes.map((l) => ({
        numeroCompte: l.numeroCompte,
        debit: saisieMontant(l.debit),
        credit: saisieMontant(l.credit),
      })),
    );
    this.erreurFormulaire.set(null);
    this.enregistree.set(false);
  }

  fermer(): void {
    this.ouverte.set(null);
    this.erreurFormulaire.set(null);
    this.enregistree.set(false);
  }

  changerLigne(index: number, champ: keyof LigneSaisie, valeur: string): void {
    this.lignes.update((lignes) => lignes.map((l, i) => (i === index ? { ...l, [champ]: valeur } : l)));
    this.enregistree.set(false);
  }

  changerLibelle(valeur: string): void {
    this.libelle.set(valeur);
    this.enregistree.set(false);
  }

  /** RG2 à RG4 : enregistrement ; refus du backend affiché sans rien changer. */
  enregistrer(): void {
    const ecriture = this.ouverte();
    if (!ecriture || !this.enregistrable()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreurFormulaire.set(null);
    const correction = {
      libelle: this.libelle().trim(),
      lignes: this.lignes().map((l, i) => ({
        numeroCompte: l.numeroCompte.trim(),
        debit: this.montants()[i].debit ?? 0,
        credit: this.montants()[i].credit ?? 0,
      })),
    };
    this.service.modifier(this.dossierId(), ecriture.id, correction).subscribe({
      next: (modifiee) => {
        this.envoiEnCours.set(false);
        this.remplacer(modifiee);
        this.ouvrir(modifiee);
        this.enregistree.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.envoiEnCours.set(false);
        this.erreurFormulaire.set(this.message(err));
      },
    });
  }

  private remplacer(modifiee: EcritureJournal): void {
    this.journal.update((journal) => {
      if (!journal) {
        return journal;
      }
      const ecritures = journal.ecritures.map((e) => (e.id === modifiee.id ? modifiee : e));
      return {
        ...journal,
        ecritures,
        totalDebit: ecritures.reduce((t, e) => t + e.totalDebit, 0),
        totalCredit: ecritures.reduce((t, e) => t + e.totalCredit, 0),
      };
    });
  }

  private message(err: HttpErrorResponse): string {
    if (err.status === 404) {
      return MESSAGE_INTROUVABLE;
    }
    return [400, 409].includes(err.status) && err.error?.message ? err.error.message : MESSAGE_INDISPONIBLE;
  }

  protected montant(valeur: number): string {
    return valeur === 0 ? '' : euros(valeur);
  }

  protected total(valeur: number): string {
    return euros(valeur);
  }

  protected date(jour: string): string {
    return dateOuTiret(jour);
  }

  protected quand(instant: string): string {
    return formaterDepot(instant);
  }

  protected valeur(evenement: Event): string {
    return (evenement.target as HTMLInputElement | HTMLSelectElement).value;
  }
}

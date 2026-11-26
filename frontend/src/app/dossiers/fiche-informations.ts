import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import {
  CHAMPS,
  ChampFacture,
  FactureService,
  InformationsFacture,
  LIBELLES_CHAMP,
  Lecture,
  VueFacture,
  formaterDepot,
  modifiable,
  montantOuTiret,
  taux,
  valeurChamp,
} from './facture.service';
import { CompteComptable, PlanComptableService } from './plan-comptable.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** Champs du formulaire, tels que saisis (montants avec une virgule : « 186,40 »). */
export interface Saisie {
  typeTiers: '' | 'FOURNISSEUR' | 'CLIENT';
  nomTiers: string;
  numeroFacture: string;
  dateEmission: string;
  montantHt: string;
  montantTva: string;
  tauxTva: string;
  montantTtc: string;
  compteContrepartie: string;
}

/** Lecture -> formulaire : champs vides pour ce qui n'est pas renseigné. */
export function saisieDe(lecture: Lecture): Saisie {
  return {
    typeTiers: lecture.typeTiers ?? '',
    nomTiers: lecture.nomTiers ?? '',
    numeroFacture: lecture.numeroFacture ?? '',
    dateEmission: lecture.dateEmission ?? '',
    montantHt: nombreEnTexte(lecture.montantHt),
    montantTva: nombreEnTexte(lecture.montantTva),
    tauxTva: nombreEnTexte(lecture.tauxTva),
    montantTtc: nombreEnTexte(lecture.montantTtc),
    compteContrepartie: lecture.compteContrepartie ?? '',
  };
}

function nombreEnTexte(valeur: number | null): string {
  return valeur === null ? '' : String(valeur).replace('.', ',');
}

/** « 1 186,40 € » -> 1186.4 ; vide -> null ; sinon le message d'erreur à afficher. */
export function nombre(texte: string, libelle: string): number | null | string {
  const nettoye = texte.replace(/[\s  €%]/g, '').replace(',', '.');
  if (nettoye === '') {
    return null;
  }
  const valeur = Number(nettoye);
  return Number.isFinite(valeur) ? valeur : `${libelle} : nombre attendu`;
}

/** Formulaire -> informations envoyées au backend, ou le premier message d'erreur de saisie. */
export function informationsDe(saisie: Saisie): InformationsFacture | string {
  const montants = {
    montantHt: nombre(saisie.montantHt, 'Montant HT'),
    montantTva: nombre(saisie.montantTva, 'Montant TVA'),
    tauxTva: nombre(saisie.tauxTva, 'Taux de TVA'),
    montantTtc: nombre(saisie.montantTtc, 'Montant TTC'),
  };
  const erreur = Object.values(montants).find((valeur) => typeof valeur === 'string');
  if (typeof erreur === 'string') {
    return erreur;
  }
  return {
    typeTiers: saisie.typeTiers === '' ? null : saisie.typeTiers,
    nomTiers: saisie.nomTiers.trim() || null,
    numeroFacture: saisie.numeroFacture.trim() || null,
    dateEmission: saisie.dateEmission || null,
    montantHt: montants.montantHt as number | null,
    montantTva: montants.montantTva as number | null,
    tauxTva: montants.tauxTva as number | null,
    montantTtc: montants.montantTtc as number | null,
    compteContrepartie: saisie.compteContrepartie || null,
  };
}

/**
 * US-53, US-54 : champs lus sur la facture, « À vérifier » ou « À renseigner ».
 * US-55 : un clic sur un champ (ou son repère) met la fiche en modification, le curseur sur ce champ
 * (RG2) ; contrepartie choisie dans le plan comptable du dossier (RG3) ; « Enregistrer », « Valider »
 * (RG4 à RG6) ou « Valider et passer à la suivante ». Lecture échouée : saisie à la main (RG7).
 * Une facture en cours de lecture ne se modifie pas. US-56 : une facture d'achat « Traitée » se corrige
 * (« Enregistrer » seulement) tant que son écriture n'est pas exportée ; une facture de vente « Traitée »
 * ne se modifie plus.
 */
@Component({
  imports: [FormsModule, NgTemplateOutlet],
  selector: 'app-fiche-informations',
  templateUrl: './fiche-informations.html',
  styleUrl: './fiche-informations.scss',
})
export class FicheInformations {
  private readonly factures = inject(FactureService);
  private readonly plans = inject(PlanComptableService);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly dossierId = input.required<string>();
  readonly facture = input.required<VueFacture>();
  /** Fiche enregistrée ou validée : la page affiche la version à jour. */
  readonly modifiee = output<VueFacture>();
  /** « Valider et passer à la suivante » : facture validée, la page ouvre la suivante à traiter. */
  readonly validee = output<VueFacture>();

  protected readonly champs = CHAMPS;
  protected readonly libelles = LIBELLES_CHAMP;
  protected readonly edition = signal(false);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly comptes = signal<CompteComptable[]>([]);
  protected saisie: Saisie = saisieDe(this.lectureVide());

  protected modifiable(f: VueFacture): boolean {
    return modifiable(f);
  }

  /** RG2 : la fiche passe en modification, le curseur sur le champ cliqué. */
  modifier(lecture: Lecture, champ: ChampFacture): void {
    this.saisie = saisieDe(lecture);
    this.erreur.set(null);
    this.edition.set(true);
    if (this.comptes().length === 0) {
      this.plans.consulter(Number(this.dossierId())).subscribe({
        next: (plan) => this.comptes.set(plan.comptes.filter((c) => c.actif)),
        error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
      });
    }
    setTimeout(() => this.hote.nativeElement.querySelector<HTMLElement>(`#champ-${champ}`)?.focus());
  }

  annuler(): void {
    this.edition.set(false);
    this.erreur.set(null);
  }

  enregistrer(): void {
    this.envoyer((informations) =>
      this.factures.enregistrerInformations(this.dossierId(), this.facture().id, informations),
    );
  }

  valider(passerALaSuivante: boolean): void {
    this.envoyer(
      (informations) => this.factures.valider(this.dossierId(), this.facture().id, informations),
      passerALaSuivante,
    );
  }

  private envoyer(
    appel: (informations: InformationsFacture) => Observable<VueFacture>,
    passerALaSuivante = false,
  ): void {
    const informations = informationsDe(this.saisie);
    if (typeof informations === 'string') {
      this.erreur.set(informations);
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    appel(informations).subscribe({
      next: (vue) => {
        this.envoiEnCours.set(false);
        this.edition.set(false);
        this.modifiee.emit(vue);
        if (passerALaSuivante) {
          this.validee.emit(vue);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.envoiEnCours.set(false);
        this.erreur.set([400, 409].includes(err.status) && err.error?.message ? err.error.message : MESSAGE_INDISPONIBLE);
      },
    });
  }

  /** Compte saisi absent de la liste (désactivé depuis) : gardé pour ne pas le perdre. */
  protected compteHorsListe(): boolean {
    const numero = this.saisie.compteContrepartie;
    return numero !== '' && !this.comptes().some((c) => c.numero === numero);
  }

  protected valeur(lecture: Lecture, champ: ChampFacture): string | null {
    return valeurChamp(lecture, champ);
  }

  protected taux(valeur: number): string {
    return taux(valeur);
  }

  protected montant(valeur: number | null): string {
    return montantOuTiret(valeur);
  }

  protected date(instant: string): string {
    return formaterDepot(instant);
  }

  private lectureVide(): Lecture {
    return {
      statut: 'LUE',
      typeTiers: null,
      nomTiers: null,
      numeroFacture: null,
      dateEmission: null,
      montantHt: null,
      montantTva: null,
      tauxTva: null,
      tauxMultiple: false,
      detailTva: [],
      montantTtc: null,
      compteContrepartie: null,
      libelleContrepartie: null,
      aVerifier: [],
      aRenseigner: [],
    };
  }
}

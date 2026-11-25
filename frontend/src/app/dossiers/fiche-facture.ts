import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import {
  CHAMPS,
  ChampFacture,
  FactureService,
  InformationsFacture,
  LIBELLES_CHAMP,
  LIBELLES_STATUT,
  MESSAGE_MOTIF_MANQUANT,
  ReponseFiche,
  VueFacture,
} from './facture.service';
import { CompteComptable, PlanComptableService } from './plan-comptable.service';

const MESSAGE_INTROUVABLE = 'Facture introuvable ou accès non autorisé';
const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
export const MESSAGE_ENREGISTREE = 'Modifications enregistrées.';
export const MESSAGE_VALIDEE = 'Facture validée : elle est maintenant « Traitée ».';
export const MESSAGE_PLUS_DE_SUIVANTE = 'Facture validée. Plus aucune facture à traiter dans cet onglet.';
export const MESSAGE_A_COMPLETER = 'Facture passée en « À compléter » : le client voit le motif dans son espace.';

/** Valeurs du formulaire, en texte (montants saisis « 186,40 » ou « 186.40 »). */
export type ValeursFiche = Record<keyof InformationsFacture, string>;

/** Champ du formulaire de chaque champ de la fiche (RG1). */
export const CONTROLES: Record<ChampFacture, keyof InformationsFacture> = {
  TYPE_TIERS: 'typeTiers',
  NOM_TIERS: 'nomTiers',
  NUMERO_FACTURE: 'numeroFacture',
  DATE_EMISSION: 'dateEmission',
  MONTANT_HT: 'montantHt',
  MONTANT_TVA: 'montantTva',
  TAUX_TVA: 'tauxTva',
  MONTANT_TTC: 'montantTtc',
  COMPTE_CONTREPARTIE: 'compteContrepartie',
};

/** Champs saisis en nombres, avec le libellé du message « nombre invalide ». */
const NOMBRES = {
  montantHt: LIBELLES_CHAMP.MONTANT_HT,
  montantTva: LIBELLES_CHAMP.MONTANT_TVA,
  tauxTva: LIBELLES_CHAMP.TAUX_TVA,
  montantTtc: LIBELLES_CHAMP.MONTANT_TTC,
} as const;

/** 155.33 -> « 155,33 » ; 20 -> « 20 » (taux) ; vide -> « ». */
export function versFormulaire(infos: InformationsFacture): ValeursFiche {
  const montant = (v: number | null) => (v === null ? '' : v.toFixed(2).replace('.', ','));
  return {
    typeTiers: infos.typeTiers ?? '',
    nomTiers: infos.nomTiers ?? '',
    numeroFacture: infos.numeroFacture ?? '',
    dateEmission: infos.dateEmission ?? '',
    montantHt: montant(infos.montantHt),
    montantTva: montant(infos.montantTva),
    tauxTva: infos.tauxTva === null ? '' : String(infos.tauxTva).replace('.', ','),
    montantTtc: montant(infos.montantTtc),
    compteContrepartie: infos.compteContrepartie ?? '',
  };
}

/** « 1 250,50 » -> 1250.5 ; vide -> null ; pas un nombre -> undefined. */
export function nombre(texte: string): number | null | undefined {
  const nettoye = texte.replace(/[\s  €%]/g, '').replace(',', '.');
  if (nettoye === '') {
    return null;
  }
  return /^-?\d+(\.\d+)?$/.test(nettoye) ? Number(nettoye) : undefined;
}

/** Corps envoyé au backend, ou le message du premier nombre mal saisi. */
export function depuisFormulaire(valeurs: ValeursFiche): InformationsFacture | string {
  const nombres: Partial<Record<keyof typeof NOMBRES, number | null>> = {};
  for (const [champ, libelle] of Object.entries(NOMBRES) as [keyof typeof NOMBRES, string][]) {
    const valeur = nombre(valeurs[champ]);
    if (valeur === undefined) {
      return `${libelle} : nombre invalide`;
    }
    nombres[champ] = valeur;
  }
  const texte = (v: string) => (v.trim() === '' ? null : v.trim());
  return {
    typeTiers: valeurs.typeTiers === 'FOURNISSEUR' || valeurs.typeTiers === 'CLIENT' ? valeurs.typeTiers : null,
    nomTiers: texte(valeurs.nomTiers),
    numeroFacture: texte(valeurs.numeroFacture),
    dateEmission: texte(valeurs.dateEmission),
    montantHt: nombres.montantHt ?? null,
    montantTva: nombres.montantTva ?? null,
    tauxTva: nombres.tauxTva ?? null,
    montantTtc: nombres.montantTtc ?? null,
    compteContrepartie: texte(valeurs.compteContrepartie),
  };
}

/**
 * US-55 Fiche facture : les informations lues (US-53, US-54) à vérifier, corriger et valider, à côté du
 * document (page d'aperçu, US-13). RG2 : champs « À vérifier » ou « À renseigner » mis en évidence, tous
 * modifiables ; RG3 : contrepartie choisie dans le plan comptable du dossier (US-41) ; RG4, RG5 : refus du
 * backend affiché tel quel ; RG6 : « Valider » et « Valider et passer à la suivante » ; RG7 : même fiche
 * pour une facture à saisir à la main ; RG8 : facture traitée en lecture seule ; CA5 : « À compléter ».
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-fiche-facture',
  templateUrl: './fiche-facture.html',
  styleUrl: './fiche-facture.scss',
})
export class FicheFacture implements OnInit {
  readonly dossierId = input.required<string>();
  readonly facture = input.required<VueFacture>();
  /** Fiche à jour après un enregistrement, une validation ou un passage en « À compléter ». */
  readonly misAJour = output<VueFacture>();

  private readonly factures = inject(FactureService);
  private readonly plans = inject(PlanComptableService);
  private readonly router = inject(Router);

  protected readonly champs = CHAMPS;
  protected readonly controles = CONTROLES;
  protected readonly libelles = LIBELLES_CHAMP;
  protected readonly comptes = signal<CompteComptable[]>([]);
  protected readonly planIndisponible = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly succes = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected readonly demandeComplement = signal(false);
  protected readonly formulaire = inject(FormBuilder).nonNullable.group<ValeursFiche>(
    versFormulaire({
      typeTiers: null,
      nomTiers: null,
      numeroFacture: null,
      dateEmission: null,
      montantHt: null,
      montantTva: null,
      tauxTva: null,
      montantTtc: null,
      compteContrepartie: null,
    }),
  );
  protected readonly motif = new FormControl('', { nonNullable: true });
  private readonly valeurs = signal<ValeursFiche>(this.formulaire.getRawValue());

  protected readonly fiche = computed(() => this.facture().fiche);
  protected readonly statut = computed(() => LIBELLES_STATUT[this.fiche().statutTraitement]);
  /** US-51 RG2 : « À compléter » seulement depuis Reçue ou En cours de traitement. */
  protected readonly peutCompleter = computed(() => ['RECUE', 'EN_COURS'].includes(this.fiche().statutTraitement));
  /** RG3 : comptes actifs du plan ; la contrepartie lue hors du plan reste visible, signalée. */
  protected readonly horsPlan = computed(() => {
    const compte = this.valeurs().compteContrepartie;
    return compte !== '' && !this.comptes().some((c) => c.numero === compte) ? compte : null;
  });

  constructor() {
    this.formulaire.valueChanges.subscribe(() => this.valeurs.set(this.formulaire.getRawValue()));
    // Nouvelle fiche (ouverture, enregistrement, validation) : le formulaire reprend ses valeurs.
    effect(() => {
      const fiche = this.fiche();
      this.formulaire.reset(versFormulaire(fiche.informations));
      if (fiche.modifiable) {
        this.formulaire.enable();
      } else {
        this.formulaire.disable();
      }
    });
  }

  ngOnInit(): void {
    this.plans.consulter(Number(this.dossierId())).subscribe({
      next: (plan) => this.comptes.set(plan.comptes.filter((c) => c.actif)),
      error: () => this.planIndisponible.set(true),
    });
  }

  /** RG2 : champ lu avec un doute, tant qu'il n'a pas été corrigé et enregistré. */
  protected aVerifier(champ: ChampFacture): boolean {
    return this.facture().lecture?.aVerifier.includes(champ) ?? false;
  }

  /** RG2 : champ vide (non lu, ou à saisir à la main, RG7). */
  protected aRenseigner(champ: ChampFacture): boolean {
    if (champ === 'TAUX_TVA' && this.facture().lecture?.tauxMultiple) {
      return false;
    }
    return this.valeurs()[CONTROLES[champ]].trim() === '';
  }

  protected enregistrer(): void {
    this.envoyer((infos) => this.factures.enregistrer(this.dossierId(), this.facture().id, infos), () =>
      this.succes.set(MESSAGE_ENREGISTREE),
    );
  }

  /** RG6 : « Valider » ; avecSuivante : ouvre la prochaine facture à traiter de l'onglet. */
  protected valider(avecSuivante: boolean): void {
    this.envoyer((infos) => this.factures.valider(this.dossierId(), this.facture().id, infos), (reponse) => {
      if (avecSuivante && reponse.suivante !== null) {
        void this.router.navigate(['/dossiers', this.dossierId(), 'factures', reponse.suivante]);
        return;
      }
      this.succes.set(avecSuivante ? MESSAGE_PLUS_DE_SUIVANTE : MESSAGE_VALIDEE);
    });
  }

  protected ouvrirComplement(): void {
    this.demandeComplement.set(true);
    this.motif.reset();
    this.erreur.set(null);
  }

  protected annulerComplement(): void {
    this.demandeComplement.set(false);
  }

  /** RG5, CA5 : demander l'information au client, avec un motif (US-51 RG3). */
  protected passerACompleter(): void {
    const motif = this.motif.value.trim();
    if (!motif) {
      this.erreur.set(MESSAGE_MOTIF_MANQUANT);
      return;
    }
    this.debut();
    this.factures.changerStatut(this.dossierId(), this.facture().id, 'A_COMPLETER', motif).subscribe({
      next: (ligne) => {
        this.envoi.set(false);
        this.demandeComplement.set(false);
        const facture = this.facture();
        this.misAJour.emit({
          ...facture,
          fiche: { ...facture.fiche, statutTraitement: ligne.statut, motif: ligne.motif },
        });
        this.succes.set(MESSAGE_A_COMPLETER);
      },
      error: (err: HttpErrorResponse) => this.echec(err),
    });
  }

  private envoyer(
    appel: (infos: InformationsFacture) => Observable<ReponseFiche>,
    apres: (reponse: ReponseFiche) => void,
  ): void {
    const infos = depuisFormulaire(this.formulaire.getRawValue());
    if (typeof infos === 'string') {
      this.succes.set(null);
      this.erreur.set(infos);
      return;
    }
    this.debut();
    appel(infos).subscribe({
      next: (reponse) => {
        this.envoi.set(false);
        this.misAJour.emit(reponse.facture);
        apres(reponse);
      },
      error: (err: HttpErrorResponse) => this.echec(err),
    });
  }

  private debut(): void {
    this.envoi.set(true);
    this.erreur.set(null);
    this.succes.set(null);
  }

  /** 400 (RG3, RG4, RG5) et 409 (RG8) : message du backend tel quel ; la saisie reste à l'écran. */
  private echec(err: HttpErrorResponse): void {
    this.envoi.set(false);
    if ((err.status === 400 || err.status === 409) && err.error?.message) {
      this.erreur.set(String(err.error.message));
    } else {
      this.erreur.set(err.status === 404 || err.status === 403 ? MESSAGE_INTROUVABLE : MESSAGE_INDISPONIBLE);
    }
  }
}

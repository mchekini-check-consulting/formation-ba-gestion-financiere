import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { FactureService, VueFacture } from './facture.service';

/** US-13 RG3 : facture d'un dossier sans droits, inexistante ou adresse tapée à la main. */
export const MESSAGE_FACTURE_INTROUVABLE = 'Facture introuvable ou accès non autorisé';
const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** Comment afficher le fichier : PDF dans un cadre, image, ou pas d'aperçu possible. */
export type Affichage = 'pdf' | 'image' | 'aucun';

export function affichage(typeFichier: string): Affichage {
  if (typeFichier === 'application/pdf') {
    return 'pdf';
  }
  return typeFichier.startsWith('image/') ? 'image' : 'aucun';
}

/**
 * US-13 Aperçu et téléchargement d'une facture. Page réservée aux personnes connectées (RG3 :
 * sans connexion, retour à l'écran de connexion) ; le fichier n'est envoyé par le backend qu'avec
 * la session et les droits sur le dossier. Ouvrir l'aperçu retire le badge « Nouvelle ».
 */
@Component({
  imports: [RouterLink],
  selector: 'app-apercu-facture',
  templateUrl: './apercu-facture.html',
  styleUrl: './apercu-facture.scss',
})
export class ApercuFacture implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly factures = inject(FactureService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly dossierId = signal('');
  protected readonly facture = signal<VueFacture | null>(null);
  protected readonly lien = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly telechargementEnCours = signal(false);
  private adresseFichier: string | null = null;

  ngOnInit(): void {
    const parametres = this.route.snapshot.paramMap;
    const dossierId = parametres.get('id') ?? '';
    const factureId = parametres.get('factureId') ?? '';
    this.dossierId.set(dossierId);
    this.factures
      .consulter(dossierId, factureId)
      .pipe(
        switchMap((facture) => {
          this.facture.set(facture);
          return this.factures.fichier(dossierId, factureId, false);
        }),
      )
      .subscribe({
        next: (fichier) => {
          this.adresseFichier = URL.createObjectURL(fichier);
          this.lien.set(this.adresseFichier);
        },
        error: (err: HttpErrorResponse) => this.echec(err),
      });
  }

  ngOnDestroy(): void {
    if (this.adresseFichier) {
      URL.revokeObjectURL(this.adresseFichier);
    }
  }

  /** RG2 : le fichier d'origine, enregistré sous son nom. */
  telecharger(): void {
    const facture = this.facture();
    if (!facture) {
      return;
    }
    this.telechargementEnCours.set(true);
    this.factures.fichier(this.dossierId(), facture.id, true).subscribe({
      next: (fichier) => {
        this.telechargementEnCours.set(false);
        const adresse = URL.createObjectURL(fichier);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = facture.nomFichier;
        lien.click();
        URL.revokeObjectURL(adresse);
      },
      error: (err: HttpErrorResponse) => {
        this.telechargementEnCours.set(false);
        this.echec(err);
      },
    });
  }

  protected affichage(facture: VueFacture): Affichage {
    return affichage(facture.typeFichier);
  }

  /** Adresse locale du fichier déjà reçu (blob:), jamais une adresse venue d'ailleurs. */
  protected cadre(lien: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(lien);
  }

  protected onglet(facture: VueFacture | null): string {
    return facture?.sens === 'VENTE' ? 'clients' : 'fournisseurs';
  }

  private echec(err: HttpErrorResponse): void {
    this.erreur.set([400, 403, 404].includes(err.status) ? MESSAGE_FACTURE_INTROUVABLE : MESSAGE_INDISPONIBLE);
  }
}

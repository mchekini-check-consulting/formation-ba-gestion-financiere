import { Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../connexion/auth.service';
import { InactiviteService } from '../session/inactivite.service';
import { SessionService } from '../session/session.service';
import { ModulesMenu } from '../modules/modules-menu';

/**
 * Cadre des pages connectées (maquette « Liste des dossiers ») : menu, « Se déconnecter »
 * (US-07 RG1) et avertissement 2 minutes avant l'expiration (US-07 RG2, maquette « Session expirée »).
 */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ModulesMenu],
  selector: 'app-espace',
  styleUrl: './espace.scss',
  templateUrl: './espace.html',
})
export class Espace implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly inactivite = inject(InactiviteService);

  protected readonly utilisateur = this.session.utilisateur;
  protected readonly collaborateur = computed(() => this.utilisateur()?.role === 'COLLABORATEUR');
  protected readonly initiales = computed(() =>
    (this.utilisateur()?.nomAffiche ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .map((mot) => mot[0].toUpperCase())
      .slice(0, 2)
      .join(''),
  );
  protected readonly compteARebours = computed(() => {
    const secondes = this.inactivite.secondesRestantes() ?? 0;
    return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, '0')}`;
  });

  ngOnInit(): void {
    this.inactivite.demarrer();
  }

  ngOnDestroy(): void {
    this.inactivite.arreter();
  }

  /** US-07 RG1 : ferme la session et ramène à l'écran de connexion. */
  protected seDeconnecter(): void {
    const jeton = this.session.jeton();
    this.inactivite.arreter();
    this.auth.deconnecter(jeton).subscribe({ error: () => undefined });
    this.session.fermer();
    void this.router.navigate(['/connexion'], { queryParams: { raison: 'deconnexion' } });
  }
}

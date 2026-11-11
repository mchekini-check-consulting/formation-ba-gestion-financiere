import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, Scroll } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { App } from './app';
import { appConfig } from './app.config';

describe('appConfig', () => {
  let hote: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    hote = document.createElement('app-root');
    document.body.appendChild(hote);
    // Démarrage comme dans le navigateur : c'est là que le routeur active le défilement.
    TestBed.inject(ApplicationRef).bootstrap(App, hote);
  });

  afterEach(() => hote.remove());

  it('descend jusqu’à la section visée par un lien « /#tarifs »', async () => {
    const router = TestBed.inject(Router);
    const defilement = firstValueFrom(router.events.pipe(filter((e): e is Scroll => e instanceof Scroll)));

    await router.navigate(['/'], { fragment: 'tarifs' });

    expect((await defilement).anchor).toBe('tarifs');
  }, 20_000); // premier affichage de la page d'accueil : lent sur un poste chargé
});

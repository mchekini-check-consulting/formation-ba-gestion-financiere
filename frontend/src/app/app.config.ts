import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding : ?dossiers=... arrive dans l'input du même nom
    provideRouter(routes, withComponentInputBinding()),
    // Appels au backend (ItemsService)
    provideHttpClient(withFetch())
  ]
};

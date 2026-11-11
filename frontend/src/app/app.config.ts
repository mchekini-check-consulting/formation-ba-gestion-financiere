import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './session/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // anchorScrolling : un lien vers « /#tarifs » (ex. « Voir les tarifs » de « Créer un compte »)
    // ouvre la page et descend jusqu'à la section visée.
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    // Appels au backend ; le jeton de session est ajouté par authInterceptor (US-07)
    provideHttpClient(withFetch(), withInterceptors([authInterceptor]))
  ]
};

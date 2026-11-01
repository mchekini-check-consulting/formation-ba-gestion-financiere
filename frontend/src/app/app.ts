import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// Coquille de l'application : affiche la page correspondant à l'adresse (app.routes.ts)
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}

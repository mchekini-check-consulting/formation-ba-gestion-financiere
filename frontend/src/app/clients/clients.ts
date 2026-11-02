import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Client, ClientService } from './client.service';

@Component({
  imports: [RouterLink],
  selector: 'app-clients',
  styleUrl: './clients.scss',
  templateUrl: './clients.html',
})
export class Clients {
  private readonly clientService = inject(ClientService);

  protected readonly clients = signal<Client[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly totalDonnees = computed(() =>
    this.clients().reduce((sum, c) => sum + c.nombreDonneesConnectees, 0),
  );

  constructor() {
    this.clientService.lister().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}

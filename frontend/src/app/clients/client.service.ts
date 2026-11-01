import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export interface Client {
  id: number;
  nomClient: string;
  nomEntreprise: string;
  nombreDonneesConnectees: number;
}

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/clients`;

  lister(): Observable<Client[]> {
    return this.http.get<Client[]>(this.url);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from './api';

// Comptes des cabinets (backend « cabinets » : CRUD sur /cabinets)
export interface Cabinet {
  id: number;
  nom: string;
  siren: string;
  email: string;
  nombreDossiers: number;
  creeLe: string;
}

export interface CabinetRequest {
  nom: string;
  siren: string;
  email: string;
  nombreDossiers: number;
}

@Injectable({ providedIn: 'root' })
export class CabinetsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase('cabinets')}/cabinets`;

  lister(): Observable<Cabinet[]> {
    return this.http.get<Cabinet[]>(this.url);
  }

  creer(cabinet: CabinetRequest): Observable<Cabinet> {
    return this.http.post<Cabinet>(this.url, cabinet);
  }

  modifier(id: number, cabinet: CabinetRequest): Observable<Cabinet> {
    return this.http.put<Cabinet>(`${this.url}/${id}`, cabinet);
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}

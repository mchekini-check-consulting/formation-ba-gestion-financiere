import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Clients } from './clients';

describe('Clients', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Clients],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('should list the clients returned by the API', async () => {
    const fixture = TestBed.createComponent(Clients);
    http.expectOne('/api/backend/clients').flush([
      { id: 1, nomClient: 'Sophie Martin', nomEntreprise: 'Boulangerie Martin', nombreDonneesConnectees: 3 },
      { id: 2, nomClient: 'Lucas Bernard', nomEntreprise: 'Bernard Transports', nombreDonneesConnectees: 7 },
    ]);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('tbody tr').length).toBe(2);
    expect(compiled.textContent).toContain('Boulangerie Martin');
    expect(compiled.textContent).toContain('2 clients · 10 données connectées');
  });

  it('should show an error when the API fails', async () => {
    const fixture = TestBed.createComponent(Clients);
    http.expectOne('/api/backend/clients').flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Impossible de charger les clients');
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CabinetsService } from './cabinets';

describe('CabinetsService', () => {
  let http: HttpTestingController;
  let service: CabinetsService;
  const corps = { nom: 'Cabinet Martin', siren: '812345678', email: 'a@b.fr', nombreDossiers: 3 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CabinetsService);
  });

  afterEach(() => http.verify());

  it('appelle le backend « cabinets » pour chaque opération', () => {
    service.lister().subscribe();
    http.expectOne({ method: 'GET', url: '/api/cabinets/cabinets' }).flush([]);

    service.creer(corps).subscribe();
    expect(http.expectOne({ method: 'POST', url: '/api/cabinets/cabinets' }).request.body).toEqual(
      corps,
    );

    service.modifier(4, corps).subscribe();
    expect(http.expectOne({ method: 'PUT', url: '/api/cabinets/cabinets/4' }).request.body).toEqual(
      corps,
    );

    service.supprimer(4).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/cabinets/cabinets/4' }).flush(null);
  });
});

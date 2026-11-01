import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SectionTarifs } from './section-tarifs';

describe('SectionTarifs', () => {
  let fixture: ComponentFixture<SectionTarifs>;
  let page: HTMLElement;

  const texte = (selecteur: string) =>
    page.querySelector(selecteur)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(SectionTarifs);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  it('affiche les 4 tranches et ce qui est inclus', () => {
    expect(page.querySelectorAll('.tranche').length).toBe(4);
    expect(texte('.tranche--sombre')).toContain('151 à 300 dossiers');
    expect(page.querySelectorAll('.inclus li').length).toBe(4);
  });

  it('calcule le tarif selon le nombre de dossiers saisi', async () => {
    expect(texte('.simulateur__total')).toMatch(/2\s300 € HT \/ mois/);
    expect(page.querySelectorAll('.simulateur__ligne').length).toBe(3);
    expect(texte('.simulateur__moyenne')).toMatch(/7,67 €/);

    const saisie = page.querySelector<HTMLInputElement>('input[name=dossiers]')!;
    saisie.value = '40';
    saisie.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelectorAll('.simulateur__ligne').length).toBe(1);
    expect(texte('.simulateur__total')).toMatch(/360 €/);

    saisie.value = '';
    saisie.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(texte('.simulateur__total')).toMatch(/^Total\s*0 €/);
    expect(page.querySelector('.simulateur__moyenne')).toBeNull();
  });

  it('transmet le nombre de dossiers simulé à la création du compte', () => {
    expect(page.querySelector('.simulateur__bouton')?.getAttribute('href')).toBe(
      '/cabinets?dossiers=300',
    );
  });
});

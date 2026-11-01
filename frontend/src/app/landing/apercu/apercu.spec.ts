import { TestBed } from '@angular/core/testing';
import { Apercu } from './apercu';

describe('Apercu', () => {
  it('montre un dossier client avec ses factures, décrit pour les lecteurs d’écran', async () => {
    const fixture = TestBed.createComponent(Apercu);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=img]')?.getAttribute('aria-label')).toContain('Aperçu');
    expect(page.textContent).toContain('Boulangerie Leroy SARL');
    expect(page.querySelectorAll('.apercu__fichiers > div').length).toBe(3);
  });
});

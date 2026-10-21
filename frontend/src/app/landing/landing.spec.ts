import { TestBed } from '@angular/core/testing';
import { Landing } from './landing';

describe('Landing', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Landing],
    }).compileComponents();
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('should render the hero title', async () => {
    const compiled = await render();
    expect(compiled.querySelector('h1')?.textContent).toContain('Le pointage qui se fait tout seul.');
  });

  it('should toggle the hero validation', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const validate = [...compiled.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Valider',
    )!;
    validate.click();
    await fixture.whenStable();
    expect(compiled.textContent).toContain('Validé ✓');
    expect(compiled.textContent).toContain("écriture prête pour l'export");
  });

  it('should toggle a module and update the offer summary', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Modules (3)');
    compiled.querySelectorAll<HTMLElement>('.module')[2].click();
    await fixture.whenStable();
    expect(compiled.textContent).toContain('Modules (4)');
    expect(compiled.querySelectorAll('.chips span').length).toBe(4);
  });

  it('should compute the base price from the folder count', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const range = compiled.querySelector<HTMLInputElement>('input[type="range"]')!;
    range.value = '500';
    range.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    // 500 dossiers × 8 € = 4 000 € / mois
    expect(compiled.querySelector('.config__total')?.textContent?.replace(/\s/g, '')).toBe('4000€/mois');
  });

  it('should open one FAQ answer at a time', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const items = compiled.querySelectorAll<HTMLElement>('.faq__item');
    expect(items[0].classList).toContain('faq__item--open');
    items[1].querySelector<HTMLButtonElement>('.faq__question')!.click();
    await fixture.whenStable();
    expect(items[0].classList).not.toContain('faq__item--open');
    expect(items[1].classList).toContain('faq__item--open');
  });

  it('should show the thank-you message after submitting the form', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(compiled.textContent).toContain("Merci, c'est noté.");
  });
});

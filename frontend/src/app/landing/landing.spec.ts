import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Landing } from './landing';

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;
  let compiled: HTMLElement;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function button(label: string): HTMLButtonElement {
    return [...compiled.querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;
  }

  async function simulate(dossiers: string): Promise<void> {
    const input = compiled.querySelector<HTMLInputElement>('.simulator input')!;
    input.value = dossiers;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function simulatorLines(): string[] {
    return [...compiled.querySelectorAll('.simulator__line')].map((l) =>
      l.textContent!.replace(/\s/g, ''),
    );
  }

  function simulatorTotal(): string {
    return compiled.querySelector('.simulator__total strong')!.textContent!.replace(/\s/g, '');
  }

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Landing],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should render the title and the header actions', async () => {
    await render();
    expect(compiled.querySelector('h1')?.textContent).toContain(
      'De la transaction au bilan, tout se fait tout seul',
    );
    const header = compiled.querySelector('.header')!;
    expect(header.querySelector('a[href="/connexion"]')?.textContent).toContain('Se connecter');
    expect(header.querySelector('a[href="/creer-compte"]')?.textContent).toContain('Créer un compte');
  });

  it('should open the cabinets tab by default, with a badge on what is not delivered', async () => {
    await render();
    expect(compiled.querySelectorAll('.tile').length).toBe(6);
    expect(compiled.querySelectorAll('.tile .soon').length).toBe(5);
  });

  it('should switch to the entreprises tab', async () => {
    await render();
    button('Entreprises clientes').click();
    await fixture.whenStable();
    expect(compiled.querySelectorAll('.tile').length).toBe(4);
    expect(compiled.querySelector('.tile h3')?.textContent).toContain('Déposer factures');
  });

  it('should select the tab from the header menu', async () => {
    await render();
    const link = [...compiled.querySelectorAll<HTMLElement>('.nav a')].find((a) =>
      a.textContent?.includes('Pour les entreprises'),
    )!;
    link.click();
    await fixture.whenStable();
    expect(compiled.querySelectorAll('.tile').length).toBe(4);
  });

  it('should list the 4 price tiers', async () => {
    await render();
    const prices = [...compiled.querySelectorAll('.tier__price strong')].map((p) => p.textContent);
    expect(prices).toEqual(['9 €', '8 €', '7 €', '6 €']);
  });

  it('should show the default simulation for 300 folders', async () => {
    await render();
    expect(simulatorLines()).toEqual(['Tranche151à300dossiers7€/dossier', '300dossiers×7€2100€']);
    expect(simulatorTotal()).toBe('2100€');
  });

  it('should apply the price of the reached tier to all the folders', async () => {
    await render();
    for (const [dossiers, total] of [
      ['1', '9€'],
      ['50', '450€'],
      ['51', '408€'],
      ['120', '960€'],
      ['150', '1200€'],
      ['151', '1057€'],
      ['301', '1806€'],
    ]) {
      await simulate(dossiers);
      expect(simulatorTotal()).toBe(total);
    }
  });

  it('should name the last tier above 300 folders', async () => {
    await render();
    await simulate('301');
    expect(simulatorLines()).toEqual(['Trancheau-delàde3006€/dossier', '301dossiers×6€1806€']);
  });

  it('should use the singular for one folder', async () => {
    await render();
    await simulate('1');
    expect(simulatorLines()[1]).toBe('1dossier×9€9€');
  });

  it('should not show lines without a valid folder count', async () => {
    await render();
    await simulate('');
    expect(simulatorLines()).toEqual([]);
    expect(simulatorTotal()).toBe('0€');
  });

  it('should open one of the 7 FAQ answers at a time', async () => {
    await render();
    const questions = compiled.querySelectorAll<HTMLButtonElement>('.faq__question');
    expect(questions.length).toBe(7);
    expect(compiled.querySelectorAll('.faq__answer').length).toBe(1);
    questions[1].click();
    await fixture.whenStable();
    expect(questions[0].getAttribute('aria-expanded')).toBe('false');
    expect(questions[1].getAttribute('aria-expanded')).toBe('true');
    questions[1].click();
    await fixture.whenStable();
    expect(compiled.querySelectorAll('.faq__answer').length).toBe(0);
  });

  it('should list the legal links in the footer', async () => {
    await render();
    const legal = [...compiled.querySelectorAll('.footer__col')].at(-1)!.textContent!;
    for (const label of ['Mentions légales', 'CGU', 'Politique de confidentialité', 'Gestion des cookies', 'Sécurité']) {
      expect(legal).toContain(label);
    }
  });

  describe('cookies', () => {
    it('should show the banner on the first visit', async () => {
      await render();
      expect(compiled.querySelector('.cookies')).not.toBeNull();
    });

    it('should keep the choice on the next visit', async () => {
      await render();
      button('Tout refuser').click();
      await fixture.whenStable();
      expect(compiled.querySelector('.cookies')).toBeNull();
      await render();
      expect(compiled.querySelector('.cookies')).toBeNull();
    });

    it('should keep the choice for 6 months', async () => {
      await render();
      button('Tout accepter').click();
      const { choice, expiresAt } = JSON.parse(localStorage.getItem('pointeo.cookies')!);
      const expected = new Date();
      expected.setMonth(expected.getMonth() + 6);
      expect(choice).toBe('accepted');
      expect(new Date(expiresAt).toDateString()).toBe(expected.toDateString());
    });

    it('should ask again once the choice has expired', async () => {
      const expiresAt = new Date(Date.now() - 1000);
      localStorage.setItem('pointeo.cookies', JSON.stringify({ choice: 'refused', expiresAt }));
      await render();
      expect(compiled.querySelector('.cookies')).not.toBeNull();
    });

    it('should ignore an unreadable stored choice', async () => {
      localStorage.setItem('pointeo.cookies', 'illisible');
      await render();
      expect(compiled.querySelector('.cookies')).not.toBeNull();
    });

    it('should reopen the banner from the footer', async () => {
      await render();
      button('Tout refuser').click();
      await fixture.whenStable();
      button('Gestion des cookies').click();
      await fixture.whenStable();
      expect(compiled.querySelector('.cookies')).not.toBeNull();
    });
  });
});

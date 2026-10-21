import { Component, computed, signal } from '@angular/core';

interface Module {
  icon: string;
  name: string;
  desc: string;
  soon?: boolean;
}

interface Faq {
  q: string;
  a: string;
}

const PRICE_PER_FOLDER_PER_MONTH = 8;

@Component({
  selector: 'app-landing',
  styleUrl: './landing.scss',
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly modules: Module[] = [
    { icon: 'NF', name: 'Notes de frais et OCR', desc: 'Le salarié photographie son ticket, Pointéo le lit.' },
    { icon: 'RL', name: 'Relances automatiques', desc: 'Pointéo réclame les justificatifs manquants au client.' },
    { icon: 'TVA', name: 'Préparation de la TVA', desc: 'Calcul automatique à partir des écritures.' },
    { icon: 'TR', name: 'Trésorerie chaque matin', desc: 'Soldes et prévisions par client.' },
    { icon: 'CL', name: 'Tableau de bord de clôture', desc: 'Où en est chaque dossier, ce qui bloque.' },
    { icon: 'RF', name: 'Rapports financiers', desc: 'Synthèses mensuelles prêtes à envoyer au dirigeant.' },
    { icon: 'EC', name: 'Espace client', desc: 'Le dirigeant dépose ses factures et suit sa trésorerie.' },
    {
      icon: 'IA',
      name: 'Assistant IA',
      desc: 'Il prépare des pistes de conseil. Le comptable valide toujours.',
      soon: true,
    },
  ];

  protected readonly faqs: Faq[] = [
    {
      q: 'Faut-il changer de logiciel comptable ?',
      a: 'Non. Pointéo se branche sur votre logiciel actuel et y exporte les écritures (FEC). Aucune migration.',
    },
    {
      q: 'Mes clients doivent-ils donner leurs codes bancaires ?',
      a: "Non. La connexion passe par un agrégateur agréé (DSP2). Le client s'identifie directement auprès de sa banque. Ni vous ni Pointéo ne voyez ses codes.",
    },
    {
      q: 'Que se passe-t-il si un client refuse de connecter sa banque ?',
      a: 'Il envoie ses relevés en PDF ou CSV. Pointéo les importe et le rapprochement fonctionne de la même façon.',
    },
    {
      q: 'Où sont stockées les données ?',
      a: 'En France. Elles sont chiffrées pendant le transport et au stockage. Le traitement est conforme au RGPD.',
    },
    {
      q: 'Peut-on activer un module pour quelques dossiers seulement ?',
      a: "Oui. Chaque module s'active dossier par dossier, en un clic depuis l'espace administrateur. Vous pouvez le désactiver à tout moment.",
    },
    {
      q: 'Combien de temps pour démarrer ?',
      a: "La mise en place comprend l'installation et la formation de vos équipes. Nous fixons le calendrier ensemble lors de la démo, selon le nombre de dossiers.",
    },
  ];

  protected readonly enabled = signal([true, true, false, false, true, false, false, false]);
  protected readonly dossiers = signal(300);
  protected readonly openFaq = signal(0);
  protected readonly validated = signal(false);
  protected readonly sent = signal(false);

  protected readonly selected = computed(() => this.modules.filter((_, i) => this.enabled()[i]));
  protected readonly socleTotal = computed(() => this.dossiers() * PRICE_PER_FOLDER_PER_MONTH);

  protected format(n: number): string {
    return n.toLocaleString('fr-FR');
  }

  protected toggleModule(i: number): void {
    this.enabled.update((on) => on.map((v, j) => (j === i ? !v : v)));
  }

  protected toggleFaq(i: number): void {
    this.openFaq.update((open) => (open === i ? -1 : i));
  }

  protected setDossiers(event: Event): void {
    this.dossiers.set(Number((event.target as HTMLInputElement).value));
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    this.sent.set(true);
  }
}

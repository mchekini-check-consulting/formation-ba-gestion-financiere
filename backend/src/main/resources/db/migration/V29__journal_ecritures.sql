-- US-47 Consulter le journal et corriger une écriture (PTO-88).

-- RG1 : n° de pièce affiché au journal ; pour une écriture de facture, le n° de la facture
-- (repris ci-dessous pour les écritures déjà générées). Vide accepté : l'ancienne version du code
-- peut encore générer des écritures pendant le déploiement.
alter table ecriture add column numero_piece varchar(100);

update ecriture
set numero_piece = facture.numero_facture
from facture
where facture.id = ecriture.facture_id;

-- RG4 : dernière correction à la main (auteur et date) ; vide tant que l'écriture n'est pas corrigée,
-- remis à vide quand l'écriture est re-générée depuis sa facture (US-56 RG5).
alter table ecriture
    add column modifiee_le     timestamp with time zone,
    add column modifiee_par_id bigint references compte (id);

-- RG1 : écritures d'un dossier sur une période.
create index ecriture_dossier_date on ecriture (dossier_id, date_ecriture);

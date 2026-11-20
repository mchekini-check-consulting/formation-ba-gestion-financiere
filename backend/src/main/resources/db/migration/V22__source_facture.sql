-- US-52 Importer des factures depuis le dossier du client (PTO-93).

-- RG4 : source de la facture, affichée dans la liste du dossier : déposée par le dirigeant dans
-- son espace client (US-15, US-38) ou importée par le cabinet (US-52). Les factures déjà en base
-- ont toutes été déposées par le dirigeant (ou ajoutées en mode test) : « DEPOT_CLIENT ».
alter table facture
    add column source varchar(20) not null default 'DEPOT_CLIENT'
        check (source in ('DEPOT_CLIENT', 'IMPORT_CABINET'));

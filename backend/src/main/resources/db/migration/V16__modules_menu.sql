-- US-18 Modules à venir, affichés dans le menu de gauche : mêmes 4 modules et même ordre que la
-- section « Bientôt dans Pointéo » de la page d'accueil (RG3 : liste réglée côté serveur).
insert into module (code, nom, description, ordre) values
    ('SYNCHRONISATION_BANCAIRE', 'Synchronisation bancaire',
     'Les opérations de vos clients chaque matin, en lecture seule.', 1),
    ('RAPPROCHEMENT_AUTOMATIQUE', 'Rapprochement automatique',
     'Pointéo propose la facture de chaque opération, vous validez.', 2);

update module set ordre = 3 where code = 'CRM';
update module set ordre = 4 where code = 'SUIVI_TEMPS';

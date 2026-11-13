-- Compte de démonstration du dirigeant (V6, Sophie Martin) : créé avant les dossiers (V10, V12),
-- il n'était rattaché à aucun dossier, donc son espace client refusait de lister ses dépôts
-- (« Le service est momentanément indisponible »). On lui donne un cabinet et un dossier de
-- démonstration (noms et SIREN fictifs). Sans effet si le compte n'existe pas ou a déjà un dossier.

insert into cabinet (nom, siren, nombre_dossiers_prevus, cgu_acceptees_le, cree_le)
select 'Cabinet Test (démonstration)', '900000001', 1, now(), now()
where exists (select 1 from compte where email = 'sophie.martin@boulangerie-test.fr' and dossier_id is null)
  and exists (select 1 from compte where email = 'jean.dupont@cabinet-test.fr')
  and not exists (select 1 from cabinet where siren = '900000001');

insert into dossier (cabinet_id, raison_sociale, siren, cree_par_id, cree_le)
select cab.id, 'Boulangerie Martin', '900000002', jean.id, now()
from cabinet cab
         join compte jean on jean.email = 'jean.dupont@cabinet-test.fr'
where cab.siren = '900000001'
  and exists (select 1 from compte where email = 'sophie.martin@boulangerie-test.fr' and dossier_id is null)
  and not exists (select 1 from dossier d where d.cabinet_id = cab.id and d.siren = '900000002');

update compte
set dossier_id = (select d.id
                  from dossier d
                           join cabinet cab on cab.id = d.cabinet_id
                  where cab.siren = '900000001' and d.siren = '900000002')
where email = 'sophie.martin@boulangerie-test.fr'
  and dossier_id is null;

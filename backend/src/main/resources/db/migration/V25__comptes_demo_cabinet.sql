-- Comptes de démonstration du cabinet (noms fictifs), pour tester l'application en dev et qualif.
-- Jean Dupont (V6) a été créé avant les cabinets : V8 l'a laissé sans cabinet et V19 ne l'a pas
-- rattaché au « Cabinet Test (démonstration) » qu'elle a créé, d'où « Seuls les collaborateurs
-- d'un cabinet ont accès aux dossiers ». Il en devient l'administrateur.
-- Ajout d'une comptable, Claire Bernard, qui suit le dossier de démonstration « Boulangerie
-- Martin » (US-20). Mot de passe de démonstration : le même que les comptes de V6.
-- Sans effet si le cabinet de démonstration n'existe pas, ou si c'est déjà fait.

update compte
set cabinet_id   = (select id from cabinet where siren = '900000001'),
    role_cabinet = 'ADMINISTRATEUR'
where email = 'jean.dupont@cabinet-test.fr'
  and cabinet_id is null
  and exists (select 1 from cabinet where siren = '900000001');

insert into compte (email, mot_de_passe_hash, role, actif, nom_affiche, cabinet_id, role_cabinet)
select 'claire.bernard@cabinet-test.fr', jean.mot_de_passe_hash, 'COLLABORATEUR', true, 'Claire Bernard', cab.id,
       'COMPTABLE'
from cabinet cab
         join compte jean on jean.email = 'jean.dupont@cabinet-test.fr'
where cab.siren = '900000001'
  and not exists (select 1 from compte where lower(email) = 'claire.bernard@cabinet-test.fr');

insert into dossier_attribution (dossier_id, comptable_id, attribue_le)
select d.id, claire.id, now()
from dossier d
         join cabinet cab on cab.id = d.cabinet_id
         join compte claire on claire.email = 'claire.bernard@cabinet-test.fr'
where cab.siren = '900000001'
  and d.siren = '900000002'
  and not exists (select 1 from dossier_attribution a where a.dossier_id = d.id and a.comptable_id = claire.id);

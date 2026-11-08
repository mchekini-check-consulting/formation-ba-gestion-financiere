-- Le code de connexion (US-04) et le code de confirmation de la création de compte (US-36)
-- partent désormais par email : le numéro de mobile n'est plus demandé ni conservé.
alter table compte drop column telephone;
alter table inscription drop column telephone;

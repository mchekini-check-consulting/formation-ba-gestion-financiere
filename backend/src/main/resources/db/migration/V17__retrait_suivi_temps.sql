-- US-18 : le module « Suivi des temps » n'est plus proposé dans le menu de gauche
-- (RG3 : la liste des modules est réglée ici, côté serveur, sans changer l'écran).
delete from module where code = 'SUIVI_TEMPS';

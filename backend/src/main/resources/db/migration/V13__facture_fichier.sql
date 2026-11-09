-- US-13 Aperçu et téléchargement : le fichier d'origine de la facture (PDF ou image), rangé
-- avec elle. Vide pour une facture sans fichier : ni aperçu ni téléchargement.
alter table facture add column contenu bytea;

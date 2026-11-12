-- US-04 retirée : la session s'ouvre dès le bon mot de passe, sans code reçu par email.
-- La table des codes de connexion (créée en V7 sous le nom code_sms) ne sert plus.
drop table if exists code_sms;

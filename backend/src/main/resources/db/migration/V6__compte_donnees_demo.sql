-- Comptes de démonstration (noms fictifs), en attendant la création de compte (US-36).
-- Mot de passe de démonstration des deux comptes : Pointeo2026!
insert into compte (email, mot_de_passe_hash, role, actif) values
    ('jean.dupont@cabinet-test.fr', '$2a$10$5Xnu965aFRQ.dX60Zp7NheohPrAgbi6z3lvjtA752D5NBQy3TJTN.', 'COLLABORATEUR', true),
    ('sophie.martin@boulangerie-test.fr', '$2a$10$5Xnu965aFRQ.dX60Zp7NheohPrAgbi6z3lvjtA752D5NBQy3TJTN.', 'DIRIGEANT', true);

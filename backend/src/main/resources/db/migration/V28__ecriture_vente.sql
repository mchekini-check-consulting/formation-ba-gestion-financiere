-- US-57 Générer les écritures d'une facture client validée (PTO-98).

-- RG1 : une facture de vente validée a son écriture au journal des ventes (VT), à côté du journal
-- des achats (AC) de l'US-56. Lignes : client 411 au débit (TTC), produit au crédit (HT), TVA collectée
-- 44571 au crédit (une ligne par taux). Rien d'autre ne change : même table, mêmes règles (RG2 à RG6).
alter table ecriture drop constraint ecriture_journal_check;
alter table ecriture add constraint ecriture_journal_check check (journal in ('AC', 'VT'));

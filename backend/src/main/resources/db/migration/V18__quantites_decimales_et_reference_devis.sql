-- Les quantites deviennent decimales : on vend aussi au m2, au metre lineaire,
-- etc. La chaine complete (lignes de vente + stock) passe en numeric.
alter table lignes_devis     alter column quantite type numeric(38,2);
alter table lignes_commande  alter column quantite type numeric(38,2);
alter table lignes_facture   alter column quantite type numeric(38,2);
alter table stock_produits   alter column quantite type numeric(38,2);
alter table mouvements_stock alter column quantite       type numeric(38,2);
alter table mouvements_stock alter column quantite_apres type numeric(38,2);
alter table produits         alter column quantite_reservee type numeric(38,2);

-- Reference libre du devis (dossier/affaire ou demande client). Optionnelle.
alter table devis add column reference varchar(255);

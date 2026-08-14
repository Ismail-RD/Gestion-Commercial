-- La reservation se fait desormais a la validation d'une commande, sur le depot
-- de prelevement choisi : elle appartient donc au stock d'un depot, non au produit.
alter table stock_produits
    add column quantite_reservee numeric(38, 2) not null default 0;

alter table produits drop column quantite_reservee;

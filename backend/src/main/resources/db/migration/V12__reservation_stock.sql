-- Reservation de stock : quantite reservee par les commandes non encore
-- validees, au niveau produit (tous depots confondus). Le stock physique
-- n'est decremente qu'a la validation ; la reservation garantit qu'on ne
-- promet pas deux fois la meme quantite.
-- Disponible a la vente = somme des stocks en depots - quantite_reservee.
alter table produits add column quantite_reservee integer not null default 0;

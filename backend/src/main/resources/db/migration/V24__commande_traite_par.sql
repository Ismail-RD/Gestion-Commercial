-- Qui a traite la commande cote entrepot (validation, preparation, livraison).
-- Les bons de livraison et de preparation le donnent comme emetteur : ces
-- documents attestent d'un travail de magasin, non de la vente.
alter table commandes add column traite_par_id bigint;

alter table commandes
    add constraint commandes_traite_par_fkey
    foreign key (traite_par_id) references utilisateurs (id);

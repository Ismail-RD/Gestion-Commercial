-- V10 : le stock d'une commande est preleve par ligne, dans un depot precis.
-- Le depot est renseigne a la validation de la commande (EN_ATTENTE -> VALIDEE),
-- il reste donc nullable (une commande en attente n'a pas encore de depot).

alter table lignes_commande
    add column depot_id bigint;

alter table lignes_commande
    add constraint lignes_commande_depot_id_fkey
    foreign key (depot_id) references depots;

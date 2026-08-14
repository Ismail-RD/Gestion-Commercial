-- V5 : retablit les 3 cles etrangeres vers clients, absentes de la base.
--
-- Cause : la V2 a supprime ces FK puis fait un "drop table clients cascade" ;
-- la version reellement appliquee ne les a pas recreees. Resultat : on pouvait
-- supprimer un client et laisser ses devis/commandes/factures orphelins.

-- 1. Nettoyage des orphelins (aucune FK ne peut etre posee tant qu'il en reste).
--    Ordre de suppression : enfants avant parents.

delete from lignes_facture
where facture_id in (
    select f.id from factures f
    where f.client_id is not null
      and not exists (select 1 from clients c where c.id = f.client_id)
);
delete from factures f
where f.client_id is not null
  and not exists (select 1 from clients c where c.id = f.client_id);

delete from lignes_commande
where commande_id in (
    select o.id from commandes o
    where o.client_id is not null
      and not exists (select 1 from clients c where c.id = o.client_id)
);
delete from commandes o
where o.client_id is not null
  and not exists (select 1 from clients c where c.id = o.client_id);

delete from lignes_devis
where devis_id in (
    select d.id from devis d
    where d.client_id is not null
      and not exists (select 1 from clients c where c.id = d.client_id)
);
delete from devis d
where d.client_id is not null
  and not exists (select 1 from clients c where c.id = d.client_id);

-- 2. Retablissement des contraintes

alter table devis
    add constraint devis_client_id_fkey
    foreign key (client_id) references clients;

alter table commandes
    add constraint commandes_client_id_fkey
    foreign key (client_id) references clients;

alter table factures
    add constraint factures_client_id_fkey
    foreign key (client_id) references clients;

-- Plafond de credit par client et etat de credit associe.
-- Le client passe automatiquement a BLOQUE quand son encours (somme des
-- resteAPayer des factures non soldees) depasse son plafond ; le deblocage
-- est ensuite manuel (administrateur uniquement).
alter table clients add column plafond_credit numeric(38,2);

-- 'ACTIF' par defaut pour les clients existants ; la colonne reste NOT NULL,
-- Hibernate fournissant toujours une valeur a l'insertion.
alter table clients add column statut varchar(255) not null default 'ACTIF';

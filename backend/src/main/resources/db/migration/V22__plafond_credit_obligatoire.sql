-- Il n'existe pas de credit illimite : un client sans plafond explicite n'a
-- aucun credit accorde. L'absence de valeur (null) devient donc un plafond a 0,
-- et la colonne cesse d'etre nullable pour que l'ambiguite ne revienne pas.
update clients set plafond_credit = 0 where plafond_credit is null;

alter table clients alter column plafond_credit set default 0;
alter table clients alter column plafond_credit set not null;

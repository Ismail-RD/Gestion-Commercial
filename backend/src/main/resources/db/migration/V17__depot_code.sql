-- Un depot est desormais identifie par un code (ex. SH, AB) au lieu d'un numero.
-- On ne conserve que deux depots : SH (ex-depot 1) et AB (ex-depot 2).

alter table depots add column code varchar(10);

-- Mapping des depots conserves
update depots set code = 'SH' where numero = 1;
update depots set code = 'AB' where numero = 2;

-- Les autres depots disparaissent : on retire d'abord leur stock et leurs
-- mouvements (pas de valeur metier a conserver), puis le depot lui-meme.
delete from mouvements_stock where depot_id in (select id from depots where numero not in (1, 2));
delete from stock_produits  where depot_id in (select id from depots where numero not in (1, 2));
delete from depots where numero not in (1, 2);

-- La colonne numero (avec sa contrainte unique et son CHECK 1..5) disparait.
alter table depots drop column numero;

alter table depots alter column code set not null;
alter table depots add constraint depots_code_unique unique (code);

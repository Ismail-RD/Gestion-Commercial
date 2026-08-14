-- Un client et un fournisseur peuvent avoir plusieurs telephones et plusieurs RIB.
-- On remplace la colonne telephone unique par des tables de collection.

create table client_telephones (
    client_id bigint not null references clients on delete cascade,
    telephone varchar(255)
);

create table client_ribs (
    client_id bigint not null references clients on delete cascade,
    rib       varchar(255),
    banque    varchar(255)
);

create table fournisseur_telephones (
    fournisseur_id bigint not null references fournisseurs on delete cascade,
    telephone      varchar(255)
);

create table fournisseur_ribs (
    fournisseur_id bigint not null references fournisseurs on delete cascade,
    rib            varchar(255),
    banque         varchar(255)
);

-- Reprise de l'existant : l'unique telephone devient le premier de la liste.
insert into client_telephones (client_id, telephone)
    select id, telephone from clients where telephone is not null;
insert into fournisseur_telephones (fournisseur_id, telephone)
    select id, telephone from fournisseurs where telephone is not null;

-- La colonne unique n'a plus lieu d'etre.
alter table clients drop column telephone;
alter table fournisseurs drop column telephone;

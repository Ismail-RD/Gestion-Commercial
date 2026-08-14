-- CIN (Carte d'Identite Nationale) pour les tiers particuliers. Optionnel.
alter table clients_particulier add column cin varchar(255);
alter table fournisseurs_particulier add column cin varchar(255);

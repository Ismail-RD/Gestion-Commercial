-- V7 : le SIRET est francais. Au Maroc, l'identifiant legal d'une entreprise
-- est l'ICE (Identifiant Commun de l'Entreprise), sur 15 chiffres.

alter table clients_entreprise rename column siret to ice;
alter table fournisseurs_entreprise rename column siret to ice;

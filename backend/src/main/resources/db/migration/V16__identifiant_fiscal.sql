-- La "TVA intra" (format francais) est remplacee par l'identifiant fiscal
-- marocain : 8 chiffres, optionnel. Meme approche de validation a 3 niveaux
-- que l'ICE (formulaire, @Pattern API, contrainte CHECK en base).

alter table clients_entreprise rename column tva_intra to identifiant_fiscal;
alter table fournisseurs_entreprise rename column tva_intra to identifiant_fiscal;

-- Les anciennes valeurs (ex. "FR12345678901") ne respectent pas le nouveau
-- format : on les neutralise en NULL avant d'ajouter la contrainte.
update clients_entreprise
    set identifiant_fiscal = null
    where identifiant_fiscal is not null and identifiant_fiscal !~ '^[0-9]{8}$';
update fournisseurs_entreprise
    set identifiant_fiscal = null
    where identifiant_fiscal is not null and identifiant_fiscal !~ '^[0-9]{8}$';

alter table clients_entreprise
    add constraint clients_entreprise_if_format
    check (identifiant_fiscal is null or identifiant_fiscal ~ '^[0-9]{8}$');
alter table fournisseurs_entreprise
    add constraint fournisseurs_entreprise_if_format
    check (identifiant_fiscal is null or identifiant_fiscal ~ '^[0-9]{8}$');

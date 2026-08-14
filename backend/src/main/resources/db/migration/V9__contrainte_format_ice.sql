-- V9 : verrouille le format de l'ICE au niveau de la base.
--
-- La validation applicative (@Pattern) protege l'API, mais pas un INSERT direct
-- en SQL, un import, ou un futur service qui oublierait l'annotation. Cette
-- contrainte est le dernier rempart : la donnee invalide ne peut pas exister.
--
-- NULL reste autorise : l'ICE est optionnel (prospect, dossier en cours).
-- La chaine vide est refusee : c'est une valeur "presente mais invalide",
-- l'absence doit s'exprimer par NULL.

-- Une chaine vide eventuelle deviendrait invalide : on la normalise en NULL.
update clients_entreprise set ice = null where ice = '';
update fournisseurs_entreprise set ice = null where ice = '';

alter table clients_entreprise
    add constraint clients_entreprise_ice_format
    check (ice is null or ice ~ '^[0-9]{15}$');

alter table fournisseurs_entreprise
    add constraint fournisseurs_entreprise_ice_format
    check (ice is null or ice ~ '^[0-9]{15}$');

-- V3 : la liaison produit <-> fournisseur passe par l'entite ProduitFournisseur
-- (table produits_fournisseurs), qui porte reference_fournisseur et est_principal.
-- Le ManyToMany simple faisait doublon : on le supprime.

-- Report des liaisons existantes vers la table porteuse d'attributs,
-- pour ne perdre aucune donnee (sans doublon si le lien existe deja).
insert into produits_fournisseurs (produit_id, fournisseur_id, est_principal)
select s.produit_id, s.fournisseur_id, false
from produits_fournisseurs_simple s
where not exists (
    select 1 from produits_fournisseurs pf
    where pf.produit_id = s.produit_id
      and pf.fournisseur_id = s.fournisseur_id
);

drop table produits_fournisseurs_simple;

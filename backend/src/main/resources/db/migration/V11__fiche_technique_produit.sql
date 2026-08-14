-- Fiche technique d'un produit : le fichier (PDF ou image) est stocke sur le
-- systeme de fichiers ; la base ne conserve que son chemin relatif, pour ne pas
-- alourdir la table produits ni les sauvegardes.
alter table produits add column fiche_technique varchar(255);

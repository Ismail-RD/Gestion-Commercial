-- V4 : un depot est identifie par son seul numero (1 a 5).
-- Le nom et la localisation n'ont pas de valeur metier ici : on les supprime.

alter table depots drop column if exists nom;
alter table depots drop column if exists localisation;

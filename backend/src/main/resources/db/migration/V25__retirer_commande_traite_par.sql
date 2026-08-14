-- L'emetteur des documents est desormais celui qui les edite, plus celui qui a
-- traite la commande : ce suivi n'a plus d'usage. On retire la colonne plutot
-- que de garder une donnee que rien n'alimente ni ne lit.
alter table commandes drop constraint if exists commandes_traite_par_fkey;
alter table commandes drop column if exists traite_par_id;

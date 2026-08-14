-- V6 : rattache les clients qui n'ont pas de commercial.
--
-- Ces clients ont ete crees par le seed a un moment ou aucun utilisateur de role
-- COMMERCIAL n'existait encore. Depuis, le commercial en charge est deduit de
-- l'utilisateur qui saisit le client : ce cas ne peut plus se reproduire, mais
-- les lignes existantes doivent etre rattrapees (le champ n'est plus saisissable).

update clients
set commercial_id = (
    select u.id from utilisateurs u
    where u.role = 'COMMERCIAL'
    order by u.id
    limit 1
)
where commercial_id is null
  and exists (select 1 from utilisateurs u where u.role = 'COMMERCIAL');

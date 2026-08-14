-- L'administrateur cree le compte (nom, prenom, role, email) et l'utilisateur
-- choisit lui-meme son mot de passe via un lien recu par email. Le compte reste
-- inactif, donc inutilisable, tant que ce lien n'a pas ete suivi.
alter table utilisateurs add column if not exists token_invitation varchar(64);
alter table utilisateurs add column if not exists invitation_expire_le timestamp(6);

create unique index if not exists utilisateurs_token_invitation_key
    on utilisateurs (token_invitation);

-- Le mot de passe n'est plus obligatoire : il est vide entre la creation du
-- compte et la reponse a l'invitation.
alter table utilisateurs alter column mot_de_passe drop not null;

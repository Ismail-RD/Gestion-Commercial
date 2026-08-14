-- Envoi du devis au client par email : lien personnel (jeton), trace de sa
-- reponse et bon de commande depose a l'acceptation.
--
-- La reponse du client est purement informative : elle ne change pas le statut
-- du devis, qui reste valide manuellement dans l'application.
alter table devis add column token_client      varchar(64);
alter table devis add column date_envoi_email  timestamp(6);
alter table devis add column reponse_client    varchar(255);
alter table devis add column bon_commande      varchar(255);

-- Le jeton identifie un devis unique cote client.
alter table devis add constraint devis_token_client_unique unique (token_client);

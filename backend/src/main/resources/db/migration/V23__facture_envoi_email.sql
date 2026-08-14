-- Trace du dernier envoi de la facture au client par email, pour savoir depuis
-- l'application si elle lui a deja ete transmise (et la renvoyer au besoin).
alter table factures add column date_envoi_email timestamp(6);

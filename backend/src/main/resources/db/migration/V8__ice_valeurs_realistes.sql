-- V8 : remplace les anciennes valeurs de SIRET (8 chiffres, jeu de test) par des
-- ICE au bon format marocain (15 chiffres). La V7 avait renomme la colonne mais
-- conserve les donnees telles quelles.
--
-- Le filtre "length <> 15" garantit qu'un ICE deja saisi correctement ne sera
-- jamais ecrase : cette migration ne touche que les valeurs invalides.

update clients_entreprise set ice = '002443322000056'
where client_id = (select id from clients where email = 'tech@hotel-mogador.ma')
  and (ice is null or length(ice) <> 15);

update clients_entreprise set ice = '002667788000034'
where client_id = (select id from clients where email = 'admin@clinique-sainte-marie.ma')
  and (ice is null or length(ice) <> 15);

update fournisseurs_entreprise set ice = '001234567000045'
where fournisseur_id = (select id from fournisseurs where email = 'fournisseur@daikin.ma')
  and (ice is null or length(ice) <> 15);

update fournisseurs_entreprise set ice = '001987654000032'
where fournisseur_id = (select id from fournisseurs where email = 'fournisseur@trane.ma')
  and (ice is null or length(ice) <> 15);

update fournisseurs_entreprise set ice = '001122334000078'
where fournisseur_id = (select id from fournisseurs where email = 'fournisseur@viessmann.ma')
  and (ice is null or length(ice) <> 15);

update fournisseurs_entreprise set ice = '001556677000091'
where fournisseur_id = (select id from fournisseurs where email = 'fournisseur@atlantic.ma')
  and (ice is null or length(ice) <> 15);

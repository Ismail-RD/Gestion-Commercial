-- Le stock etait valorise au prix de vente, faute de connaitre le prix d'achat.
-- Les frais du dossier, repartis sur les lignes recues, donnent enfin un cout
-- de revient debarque : ce que la marchandise a reellement coute, rendue au
-- depot.
alter table commandes_fournisseur
    add column if not exists frais_fret       numeric(15, 2),
    add column if not exists frais_assurance  numeric(15, 2),
    add column if not exists droits_douane    numeric(15, 2),
    add column if not exists frais_transit    numeric(15, 2);

-- Cout unitaire debarque et quote-part de frais, figes a la reception : ils
-- valent pour ce dossier, meme si les taux et les frais changent ensuite.
alter table lignes_commande_fournisseur
    add column if not exists quote_part_frais  numeric(15, 2),
    add column if not exists cout_unitaire_mad numeric(15, 4);

-- Cout unitaire moyen pondere, recalcule a chaque reception. Null tant que le
-- produit n'est jamais entre par une commande fournisseur : on ne devine pas
-- ce qu'a coute un stock arrive par saisie manuelle.
alter table produits
    add column if not exists cout_revient_moyen numeric(15, 4);

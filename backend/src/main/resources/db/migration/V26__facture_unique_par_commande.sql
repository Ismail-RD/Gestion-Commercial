-- Une commande ne se facture qu'une fois : deux factures pour la meme
-- livraison doubleraient l'encours du client et le chiffre d'affaires.
-- Le service refuse deja le doublon ; la contrainte verrouille la regle
-- meme pour une ecriture qui ne passerait pas par lui.
-- commande_id reste nullable : Postgres autorise plusieurs NULL sur un
-- index unique, une facture sans commande n'est donc pas bloquee.
create unique index if not exists factures_commande_unique
    on factures (commande_id);

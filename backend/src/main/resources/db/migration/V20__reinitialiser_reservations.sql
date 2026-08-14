-- Le stock n'est plus engage a la creation d'une commande mais a sa validation,
-- qui le sort physiquement : plus rien n'alimente quantite_reservee. Les valeurs
-- posees par l'ancien comportement resteraient bloquees a vie, on les remet a 0.
update produits set quantite_reservee = 0 where quantite_reservee <> 0;

-- Chaque etape de la vie d'un document commercial porte desormais sa date, a
-- cote des donnees du document lui-meme. Sans elles, un statut dit ou en est
-- l'affaire mais jamais depuis quand : impossible de mesurer un delai de
-- preparation, de reprocher un retard de livraison ou de justifier une date de
-- reglement.

-- Devis : l'envoi au client et l'arbitrage de la remise.
ALTER TABLE devis ADD COLUMN date_envoi TIMESTAMP;
ALTER TABLE devis ADD COLUMN date_validation_remise TIMESTAMP;

-- Commande : validation, arbitrage de la remise, preparation, livraison, annulation.
ALTER TABLE commandes ADD COLUMN date_validation TIMESTAMP;
ALTER TABLE commandes ADD COLUMN date_validation_remise TIMESTAMP;
ALTER TABLE commandes ADD COLUMN date_en_preparation TIMESTAMP;
ALTER TABLE commandes ADD COLUMN date_livraison TIMESTAMP;
ALTER TABLE commandes ADD COLUMN date_annulation TIMESTAMP;

-- Commande fournisseur : les deux etapes du transit, et la premiere livraison.
-- date_reception existait deja mais etait ecrasee a chaque livraison partielle :
-- elle designe maintenant la reception complete, et la premiere arrivee a sa
-- propre colonne.
ALTER TABLE commandes_fournisseur ADD COLUMN date_transit DATE;
ALTER TABLE commandes_fournisseur ADD COLUMN date_douane DATE;
ALTER TABLE commandes_fournisseur ADD COLUMN date_premiere_reception DATE;
ALTER TABLE commandes_fournisseur ADD COLUMN date_annulation DATE;

-- Les dossiers deja receptionnes n'ont connu qu'une livraison : leur date de
-- reception est aussi celle de la premiere arrivee.
UPDATE commandes_fournisseur SET date_premiere_reception = date_reception
 WHERE date_reception IS NOT NULL;

-- Facture : la date a laquelle elle a ete soldee. Pour les factures deja
-- payees, c'est celle du dernier encaissement -- la seule date verifiable, et
-- de toute facon celle qui a fait basculer le statut.
ALTER TABLE factures ADD COLUMN date_reglement DATE;
UPDATE factures f SET date_reglement = (
        SELECT MAX(COALESCE(p.date_encaissement, p.date_paiement::date))
          FROM paiements p
         WHERE p.facture_id = f.id AND p.statut = 'ENCAISSE')
 WHERE f.statut = 'PAYEE';

package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;

/**
 * Representation commune d'une ligne de document (commande, facture...).
 */
public record LigneDocumentResponse(
        Long id,
        Long produitId,
        String reference,
        String designation,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        BigDecimal tauxTVA,
        BigDecimal remise,
        BigDecimal montantLigne,
        // Depot de prelevement (lignes de commande validees) ; null sinon
        String depotCode
) {
}

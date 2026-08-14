package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;

public record LigneCommandeFournisseurResponse(
        Long id,
        Long produitId,
        String reference,
        String referenceFournisseur,
        String designation,
        BigDecimal quantiteCommandee,
        BigDecimal quantiteRecue,
        BigDecimal prixUnitaireDevise,
        BigDecimal montantDevise,
        BigDecimal quotePartFrais,
        /** Cout de revient debarque d une unite, en dirhams. */
        BigDecimal coutUnitaireMAD
) {
}

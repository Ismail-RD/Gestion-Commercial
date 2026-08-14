package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;

public record LigneDevisResponse(
        Long id,
        Long produitId,
        String reference,
        String designation,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        BigDecimal tauxTVA,
        BigDecimal remise,
        BigDecimal montantLigne
) {
}

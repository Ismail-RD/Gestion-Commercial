package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;

public record StockProduitResponse(
        Long id,
        Long produitId,
        String produitReference,
        String produitDesignation,
        String depotCode,
        BigDecimal quantite
) {
}

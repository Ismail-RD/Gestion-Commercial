package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;

/**
 * Quantite d'un produit dans un depot donne (0 si aucun stock), dont la part
 * reservee a des commandes validees et le disponible qui en decoule.
 */
public record StockDepotResponse(
        String depotCode,
        BigDecimal quantite,
        BigDecimal quantiteReservee,
        BigDecimal disponible
) {
}

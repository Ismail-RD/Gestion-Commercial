package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vue d'ensemble du stock d'un produit : sa quantite dans chaque depot
 * (y compris les depots ou il n'a jamais eu de mouvement, a 0), son total,
 * la quantite reservee par les commandes validees non encore livrees et le
 * disponible a la vente.
 */
public record StockApercuResponse(
        Long produitId,
        String reference,
        String designation,
        String categorieNom,
        List<StockDepotResponse> depots,
        BigDecimal stockTotal,
        BigDecimal quantiteReservee,
        BigDecimal disponible
) {
}

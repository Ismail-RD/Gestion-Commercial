package com.example.gestioncommerciale.dto.filter;

import java.math.BigDecimal;

/**
 * Criteres de filtrage produit (tous optionnels). Lie automatiquement les query params.
 */
public record ProduitFilter(
        // Recherche libre : correspond a la reference OU a la designation.
        String recherche,
        String reference,
        String designation,
        Long categorieId,
        BigDecimal prixMin,
        BigDecimal prixMax
) {
}

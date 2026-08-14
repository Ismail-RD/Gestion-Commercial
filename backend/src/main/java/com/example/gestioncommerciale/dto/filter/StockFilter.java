package com.example.gestioncommerciale.dto.filter;

/**
 * Filtres des niveaux de stock.
 *
 * Par defaut, seules les lignes ayant reellement du stock sont retournees :
 * un couple (produit, depot) retombe a 0 apres une sortie n'est plus "en stock".
 * Passer inclureVides=true pour voir aussi ces lignes a zero.
 */
public record StockFilter(
        // Recherche libre : reference / designation du produit, code du depot.
        String recherche,
        Long produitId,
        String depotCode,
        Boolean inclureVides
) {
}

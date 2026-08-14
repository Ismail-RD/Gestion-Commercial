package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Lien produit <-> fournisseur avec ses attributs metier.
 */
public record ProduitFournisseurRequest(
        @NotNull Long fournisseurId,
        // Reference du produit chez ce fournisseur (celle du bon de commande)
        String referenceFournisseur,
        // Fournisseur retenu par defaut pour ce produit
        Boolean estPrincipal
) {
}

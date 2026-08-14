package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Commande saisie directement, sans passer par un devis (vente au comptoir,
 * reassort). Sert aussi a la modification complete d'une commande existante.
 */
public record CommandeRequest(
        @NotNull Long clientId,
        @NotEmpty(message = "Une commande doit comporter au moins une ligne")
        @Valid List<LigneCommandeRequest> lignes
) {
}

package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Requete d'entree ou de sortie de stock (quantite toujours positive).
 */
public record MouvementRequest(
        @NotNull Long produitId,
        @NotBlank String depotCode,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantite,
        String motif
) {
}

package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Ajustement d'inventaire : fixe le stock a une valeur absolue.
 */
public record AjustementRequest(
        @NotNull Long produitId,
        @NotBlank String depotCode,
        @NotNull @DecimalMin("0.0") BigDecimal nouvelleQuantite,
        String motif
) {
}

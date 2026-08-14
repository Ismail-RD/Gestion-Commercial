package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransfertRequest(
        @NotNull Long produitId,
        @NotBlank String depotSource,
        @NotBlank String depotDestination,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantite,
        String motif
) {
}

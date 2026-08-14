package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FactureRequest(
        @NotNull Long commandeId,
        @NotNull LocalDate dateEcheance
) {
}

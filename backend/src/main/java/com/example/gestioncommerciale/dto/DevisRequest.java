package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record DevisRequest(
        @NotNull Long clientId,
        String reference,
        @NotNull @FutureOrPresent LocalDate dateValidite,
        @NotEmpty(message = "Un devis doit contenir au moins une ligne")
        @Valid List<LigneDevisRequest> lignes
) {
}

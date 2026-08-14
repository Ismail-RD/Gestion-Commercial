package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

public record ChangementStatutRequest(
        @NotNull String statut
) {
}

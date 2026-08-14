package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotBlank;

public record CategorieRequest(
        @NotBlank String nom,
        String description
) {
}

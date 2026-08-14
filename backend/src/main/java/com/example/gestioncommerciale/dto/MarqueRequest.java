package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotBlank;

public record MarqueRequest(
        @NotBlank String nom,
        String logo,
        String telephone,
        String email,
        String adresse,
        String siteWeb
) {
}

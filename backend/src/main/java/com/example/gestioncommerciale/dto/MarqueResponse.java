package com.example.gestioncommerciale.dto;

public record MarqueResponse(
        Long id,
        String nom,
        String logo,
        String telephone,
        String email,
        String adresse,
        String siteWeb
) {
}

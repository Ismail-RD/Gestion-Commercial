package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.TypeClient;

public record ClientFilter(
        // Recherche libre : nom, prenom, email, adresse, raison sociale, ICE,
        // identifiant fiscal, CIN.
        String recherche,
        String nom,
        String email,
        TypeClient typeClient,
        Long commercialId
) {
}

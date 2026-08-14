package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.TypeFournisseur;

public record FournisseurFilter(
        // Recherche libre : nom, prenom, email, adresse, raison sociale, ICE,
        // identifiant fiscal, CIN.
        String recherche,
        String nom,
        String email,
        TypeFournisseur typeFournisseur
) {
}

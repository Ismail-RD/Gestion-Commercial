package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.TypeFournisseur;

public record ProduitFournisseurResponse(
        Long fournisseurId,
        String fournisseurNom,
        TypeFournisseur typeFournisseur,
        String referenceFournisseur,
        Boolean estPrincipal
) {
}

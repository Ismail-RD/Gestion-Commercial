package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.TypeMouvement;

public record MouvementFilter(
        // Recherche libre : reference / designation du produit, code du depot, motif.
        String recherche,
        Long produitId,
        String depotCode,
        TypeMouvement type
) {
}

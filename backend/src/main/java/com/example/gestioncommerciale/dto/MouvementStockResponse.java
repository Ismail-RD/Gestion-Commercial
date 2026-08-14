package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.TypeMouvement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MouvementStockResponse(
        Long id,
        Long produitId,
        String produitDesignation,
        String depotCode,
        TypeMouvement type,
        BigDecimal quantite,
        BigDecimal quantiteApres,
        String motif,
        LocalDateTime dateMouvement,
        String utilisateur
) {
}

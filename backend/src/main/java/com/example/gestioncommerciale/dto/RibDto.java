package com.example.gestioncommerciale.dto;

/** Coordonnees bancaires d'un tiers (client ou fournisseur). */
public record RibDto(
        String rib,
        String banque
) {
}

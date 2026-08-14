package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Reattribution d'un client a un autre commercial (depart, reorganisation...).
 * Reservee a l'admin : c'est la seule facon de changer le commercial en charge.
 */
public record ReattributionRequest(
        @NotNull Long commercialId
) {
}

package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

/** true pour rendre l'acces, false pour le retirer sans effacer le compte. */
public record ActivationRequest(
        @NotNull Boolean actif
) {
}

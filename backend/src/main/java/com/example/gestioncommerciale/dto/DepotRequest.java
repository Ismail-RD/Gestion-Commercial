package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un depot est identifie par son code (ex. SH, AB). */
public record DepotRequest(
        @NotBlank
        @Size(max = 20, message = "Le code d'un depot ne depasse pas 20 caracteres")
        String code
) {
}

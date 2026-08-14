package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DefinitionMotDePasseRequest(
        @NotBlank
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caracteres")
        String motDePasse
) {
}

package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PouvoirRoleRequest(
        // Un seuil de 100 % reviendrait a donner la marchandise : la borne haute
        // s'arrete juste avant.
        @NotNull
        @DecimalMin(value = "0", message = "Le seuil ne peut pas etre negatif")
        @DecimalMax(value = "99.99", message = "Le seuil doit rester inferieur a 100 %")
        BigDecimal seuilRemisePct,

        // Null = ce role ne fixe pas de plafond de credit.
        @DecimalMin(value = "0", message = "Le plafond ne peut pas etre negatif")
        BigDecimal plafondCreditMax
) {
}

package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Definit le plafond de credit d'un client, apres sa creation.
 * Il n'existe pas de credit illimite : {@code plafondCredit} a null equivaut a
 * 0, c'est-a-dire aucun credit accorde.
 */
public record PlafondCreditRequest(
        @DecimalMin(value = "0.0") BigDecimal plafondCredit
) {
}

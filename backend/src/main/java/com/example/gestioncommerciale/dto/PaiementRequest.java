package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Encaissement d'une facture. Les champs d'effet ne concernent que le cheque et
 * la traite ; ils sont ignores pour les autres modes.
 */
public record PaiementRequest(
        @NotNull Long factureId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal montant,
        @NotNull ModePaiement modePaiement,
        String reference,

        // --- Effet de commerce ---
        String numeroEffet,
        String banqueEmettrice,
        /** Date portee sur l effet par son emetteur. */
        LocalDate dateEmission,
        LocalDate dateReception,
        /** Date a laquelle l'effet devient payable : porte la prevision de tresorerie. */
        LocalDate dateEcheance
) {
}

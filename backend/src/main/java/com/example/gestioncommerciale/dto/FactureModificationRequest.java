package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Champs modifiables d'une facture emise. Les montants et les lignes ne le sont
 * pas : ils decoulent de la commande facturee, et le montant paye des paiements
 * enregistres. Seule l'echeance releve d'une decision commerciale.
 */
public record FactureModificationRequest(
        @NotNull LocalDate dateEcheance
) {
}

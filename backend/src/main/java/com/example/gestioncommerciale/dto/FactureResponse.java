package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.StatutFacture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FactureResponse(
        Long id,
        String numero,
        LocalDateTime dateFacture,
        LocalDate dateEcheance,
        // Jour du solde, nul tant que la facture reste due
        LocalDate dateReglement,
        StatutFacture statut,
        BigDecimal montantHT,
        BigDecimal montantTTC,
        BigDecimal montantPaye,
        BigDecimal resteAPayer,
        // Dernier envoi au client par email (null si jamais envoyee)
        LocalDateTime dateEnvoiEmail,
        Long commandeId,
        String commandeNumero,
        Long clientId,
        String clientNom,
        List<LigneDocumentResponse> lignes
) {
}

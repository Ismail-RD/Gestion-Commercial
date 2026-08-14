package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.ModePaiement;
import com.example.gestioncommerciale.entity.StatutPaiement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaiementResponse(
        Long id,
        Long factureId,
        String factureNumero,
        String clientNom,
        LocalDateTime datePaiement,
        BigDecimal montant,
        ModePaiement modePaiement,
        StatutPaiement statut,
        String reference,
        boolean estUnEffet,
        String numeroEffet,
        String banqueEmettrice,
        LocalDate dateEmission,
        LocalDate dateReception,
        LocalDate dateEcheance,
        LocalDate dateRemise,
        LocalDate dateEncaissement,
        String motifRejet
) {
}

package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.StatutCommande;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CommandeResponse(
        Long id,
        String numero,
        LocalDateTime dateCommande,
        StatutCommande statut,
        // Dates des etapes franchies : nulles tant que l'etape ne l'est pas
        LocalDateTime dateValidation,
        LocalDateTime dateValidationRemise,
        LocalDateTime dateEnPreparation,
        LocalDateTime dateLivraison,
        LocalDateTime dateAnnulation,
        BigDecimal montantHT,
        BigDecimal montantTTC,
        Long devisId,
        String devisNumero,
        Long clientId,
        String clientNom,
        Long commercialId,
        String commercialNom,
        List<LigneDocumentResponse> lignes
) {
}

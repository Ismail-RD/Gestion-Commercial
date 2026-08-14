package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.ReponseClient;
import com.example.gestioncommerciale.entity.StatutDevis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DevisResponse(
        Long id,
        String numero,
        String reference,
        LocalDateTime dateCreation,
        LocalDate dateValidite,
        StatutDevis statut,
        // Dates des etapes franchies, nulles tant qu elles ne le sont pas
        LocalDateTime dateEnvoi,
        LocalDateTime dateValidationRemise,
        BigDecimal montantHT,
        BigDecimal montantTTC,
        LocalDateTime dateReponseClient,
        String commentaireClient,
        // Suivi de l'envoi au client et de sa reponse (purement informatif :
        // le statut du devis n'en depend pas)
        LocalDateTime dateEnvoiEmail,
        ReponseClient reponseClient,
        boolean bonCommandeDepose,
        // Remise au-dela du seuil pas encore validee : le devis ne peut pas
        // partir chez le client tant que l'encadrement n'a pas tranche.
        boolean remiseAValider,
        Long clientId,
        String clientNom,
        Long commercialId,
        String commercialNom,
        List<LigneDevisResponse> lignes
) {
}

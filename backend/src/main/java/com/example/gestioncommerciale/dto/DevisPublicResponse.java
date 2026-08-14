package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.ReponseClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue du devis exposee au client via son lien personnel (sans authentification).
 * Volontairement minimale : aucune donnee interne (commercial, marges, statut
 * de gestion) n'y figure.
 */
public record DevisPublicResponse(
        String numero,
        String reference,
        LocalDateTime date,
        LocalDate dateValidite,
        BigDecimal montantHT,
        BigDecimal montantTTC,
        String clientNom,
        String societeNom,
        // Reponse deja donnee par le client (null s'il n'a pas encore repondu)
        ReponseClient reponseClient,
        LocalDateTime dateReponseClient,
        boolean bonCommandeDepose
) {
}

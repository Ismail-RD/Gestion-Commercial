package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Incoterm;
import com.example.gestioncommerciale.entity.ModeTransport;
import com.example.gestioncommerciale.entity.StatutCommandeFournisseur;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CommandeFournisseurResponse(
        Long id,
        String numero,
        StatutCommandeFournisseur statut,
        Long fournisseurId,
        String fournisseurNom,
        String depotReceptionCode,
        String acheteurNom,
        LocalDateTime dateCreation,
        LocalDate dateCommande,
        LocalDate dateArriveePrevue,
        LocalDate dateTransit,
        LocalDate dateDouane,
        LocalDate datePremiereReception,
        LocalDate dateReception,
        LocalDate dateAnnulation,
        String devise,
        BigDecimal tauxChange,
        Incoterm incoterm,
        ModeTransport modeTransport,
        String paysOrigine,
        boolean fraisTransportEnDevise,
        String transporteur,
        String referenceTransport,
        String portArrivee,
        BigDecimal montantDevise,
        /** Montant converti en dirhams au taux retenu, null si le taux manque. */
        BigDecimal montantMAD,
        BigDecimal fraisFret,
        BigDecimal fraisAssurance,
        BigDecimal droitsDouane,
        BigDecimal fraisTransit,
        BigDecimal totalFrais,
        /** Marchandise convertie plus frais : ce que le dossier a reellement coute. */
        BigDecimal coutTotalMAD,
        String observations,
        List<LigneCommandeFournisseurResponse> lignes
) {
}

package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.StatutClient;
import com.example.gestioncommerciale.entity.TypeClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClientResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        List<String> telephones,
        List<RibDto> ribs,
        String adresse,
        TypeClient typeClient,
        LocalDateTime dateCreation,
        Long commercialId,
        String commercialNom,
        // Credit
        BigDecimal plafondCredit,
        StatutClient statut,
        // Encours (factures impayees). Null en liste, calcule sur la fiche detail.
        BigDecimal encours,
        // Champs entreprise
        String raisonSociale,
        String ice,
        String identifiantFiscal,
        String contactNom,
        String contactPrenom,
        // Champs particulier
        LocalDate dateNaissance,
        String cin
) {
}

package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.TypeFournisseur;

import java.time.LocalDateTime;
import java.util.List;

public record FournisseurResponse(
        Long id,
        String nom,
        String email,
        List<String> telephones,
        List<RibDto> ribs,
        String adresse,
        TypeFournisseur typeFournisseur,
        LocalDateTime dateCreation,
        // Champs entreprise
        String raisonSociale,
        String ice,
        String identifiantFiscal,
        // Champs particulier
        String prenom,
        String cin
) {
}

package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.StatutDevis;

import java.time.LocalDate;

public record DevisFilter(
        // Recherche libre : numero, reference, nom du client, nom du commercial.
        String recherche,
        String numero,
        StatutDevis statut,
        Long clientId,
        Long commercialId,
        // Plage de dates de creation (bornes incluses)
        LocalDate dateMin,
        LocalDate dateMax
) {
}

package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.StatutFacture;

import java.time.LocalDate;

public record FactureFilter(
        // Recherche libre : numero et nom du client.
        String recherche,
        String numero,
        StatutFacture statut,
        Long clientId,
        // Plage de dates de facture (bornes incluses)
        LocalDate dateMin,
        LocalDate dateMax
) {
}

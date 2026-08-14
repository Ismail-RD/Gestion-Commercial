package com.example.gestioncommerciale.dto.filter;

import com.example.gestioncommerciale.entity.StatutCommande;

public record CommandeFilter(
        // Recherche libre : numero, nom du client, nom du commercial.
        String recherche,
        String numero,
        StatutCommande statut,
        Long clientId,
        Long devisId,
        // true : uniquement les commandes qui n'ont pas encore de facture.
        Boolean nonFacturee,
        // Plage de dates de commande (bornes incluses)
        java.time.LocalDate dateMin,
        java.time.LocalDate dateMax
) {
}

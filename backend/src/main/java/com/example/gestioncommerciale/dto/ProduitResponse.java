package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProduitResponse(
        Long id,
        String reference,
        String designation,
        String description,
        BigDecimal prixUnitaireHT,
        /** Cout de revient moyen, etabli par les receptions fournisseur. */
        BigDecimal coutRevientMoyen,
        BigDecimal tauxTVA,
        String uniteMesure,
        String ficheTechnique,
        Long categorieId,
        String categorieNom,
        List<MarqueResponse> marques,
        List<ProduitFournisseurResponse> fournisseurs
) {
}

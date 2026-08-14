package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProduitRequest(
        @NotBlank String reference,
        @NotBlank String designation,
        String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal prixUnitaireHT,
        @NotNull @DecimalMin(value = "0.0") BigDecimal tauxTVA,
        String uniteMesure,
        Long categorieId,
        List<Long> marqueIds,
        @Valid List<ProduitFournisseurRequest> fournisseurs
) {
}

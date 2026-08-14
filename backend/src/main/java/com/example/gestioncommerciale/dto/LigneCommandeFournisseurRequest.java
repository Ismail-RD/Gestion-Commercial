package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LigneCommandeFournisseurRequest(
        @NotNull Long produitId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantiteCommandee,
        // Prix dans la devise du fournisseur ; facultatif tant qu'il n'est pas connu.
        @DecimalMin(value = "0.0") BigDecimal prixUnitaireDevise,
        // Laisse vide, la reference du catalogue fournisseur est reprise.
        String referenceFournisseur
) {
}

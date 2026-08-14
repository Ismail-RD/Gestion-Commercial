package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LigneDevisRequest(
        @NotNull Long produitId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantite,
        // Remise en pourcentage (0 a 100). Optionnelle -> 0 par defaut.
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal remise,
        // Prix negocie. Optionnel : si absent, on prend le prix catalogue du produit.
        @DecimalMin(value = "0.0") BigDecimal prixUnitaire,
        // Taux de TVA. Optionnel : si absent, on prend celui du produit.
        @DecimalMin(value = "0.0") BigDecimal tauxTVA
) {
}

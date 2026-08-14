package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Ligne saisie sur une commande, a la creation comme a la modification.
 *
 * <p>Prix, TVA et remise sont facultatifs : laisses vides, ils reprennent les
 * conditions du devis d'origine si le produit y figure, sinon celles du
 * catalogue. Fournis, ils priment, ce qui permet de negocier directement sur la
 * commande comme on le fait sur un devis.
 *
 * @param depotCode depot de prelevement, requis seulement pour un produit
 *                  ajoute alors que la commande est deja validee (son stock
 *                  est reserve, il faut savoir ou prendre le supplement).
 */
public record LigneCommandeRequest(
        @NotNull Long produitId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false,
                message = "La quantite doit etre strictement positive")
        BigDecimal quantite,
        @DecimalMin(value = "0.0") BigDecimal prixUnitaire,
        @DecimalMin(value = "0.0") BigDecimal tauxTVA,
        @DecimalMin(value = "0.0") BigDecimal remise,
        String depotCode
) {
}

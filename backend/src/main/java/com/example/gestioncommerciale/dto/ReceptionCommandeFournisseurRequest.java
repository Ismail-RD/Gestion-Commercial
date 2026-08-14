package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reception d'une commande : ce qui arrive lors de <em>cette</em> livraison,
 * ligne par ligne. Une ligne omise recoit son reliquat, ce qui couvre le cas
 * courant d'une livraison complete.
 *
 * <p>Les quantites sont celles de la livraison en cours, pas un cumul : une
 * commande de 12 recue en 9 puis 3 se saisit "9" puis "3".
 */
public record ReceptionCommandeFournisseurRequest(
        @Valid List<LigneRecue> lignes
) {

    public record LigneRecue(
            @NotNull Long ligneId,
            /** Quantite arrivee lors de cette livraison. */
            @NotNull @DecimalMin(value = "0.0") BigDecimal quantiteRecue
    ) {
    }
}

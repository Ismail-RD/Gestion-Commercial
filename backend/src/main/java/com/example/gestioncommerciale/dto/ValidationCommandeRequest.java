package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Validation d'une commande : chaque ligne indique le depot d'ou preleverer
 * le stock. C'est le moment ou le stock est verifie et decremente.
 */
public record ValidationCommandeRequest(
        @NotEmpty @Valid List<LigneDepot> lignes
) {
    public record LigneDepot(
            @NotNull Long ligneId,
            @NotBlank String depotCode
    ) {
    }
}

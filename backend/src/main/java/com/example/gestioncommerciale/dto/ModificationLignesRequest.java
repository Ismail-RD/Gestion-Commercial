package com.example.gestioncommerciale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Nouvelle composition des lignes d'une commande, avant l'edition de son bon de
 * livraison. La liste remplace l'existant : une ligne absente est supprimee, une
 * ligne ajoutee reprend les conditions du devis si le produit y figurait, sinon
 * celles du catalogue.
 */
public record ModificationLignesRequest(
        @NotEmpty(message = "Une commande doit conserver au moins une ligne")
        @Valid List<LigneCommandeRequest> lignes
) {
}

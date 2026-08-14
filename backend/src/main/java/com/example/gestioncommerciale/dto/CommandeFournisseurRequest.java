package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Incoterm;
import com.example.gestioncommerciale.entity.ModeTransport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Saisie d'une commande fournisseur. Le bloc import est facultatif : un achat
 * local se passe de devise, d'incoterm et de transport.
 */
public record CommandeFournisseurRequest(
        @NotNull Long fournisseurId,
        @NotBlank String depotReceptionCode,
        /** Date reelle de la commande ; vide, l'emission pose celle du jour. */
        LocalDate dateCommande,
        LocalDate dateArriveePrevue,

        @Size(min = 3, max = 3, message = "La devise s'ecrit sur trois lettres (MAD, EUR, USD)")
        String devise,
        @DecimalMin(value = "0.0", inclusive = false,
                message = "Le taux de change doit etre strictement positif")
        BigDecimal tauxChange,
        Incoterm incoterm,
        ModeTransport modeTransport,
        String paysOrigine,
        /** true : fret et assurance sont en devise, convertis au taux du dossier. */
        Boolean fraisTransportEnDevise,
        String transporteur,
        String referenceTransport,
        String portArrivee,
        // Frais du dossier, en dirhams : douane et transit sont factures
        // localement, et un fret paye en devise se convertit au meme taux.
        @DecimalMin(value = "0.0") BigDecimal fraisFret,
        @DecimalMin(value = "0.0") BigDecimal fraisAssurance,
        @DecimalMin(value = "0.0") BigDecimal droitsDouane,
        @DecimalMin(value = "0.0") BigDecimal fraisTransit,
        String observations,

        @NotEmpty(message = "Une commande sans ligne n'a rien a commander")
        @Valid List<LigneCommandeFournisseurRequest> lignes
) {
}

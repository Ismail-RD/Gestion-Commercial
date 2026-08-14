package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.TypeFournisseur;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record FournisseurRequest(
        @NotBlank String nom,
        @Email String email,
        // Un fournisseur peut avoir plusieurs telephones et plusieurs RIB.
        List<String> telephones,
        List<RibDto> ribs,
        String adresse,
        @NotNull TypeFournisseur typeFournisseur,
        // Champs entreprise
        String raisonSociale,
        // ICE marocain : exactement 15 chiffres. Optionnel (prospect, dossier en
        // cours), mais s'il est saisi il doit etre au bon format.
        @Pattern(regexp = "^$|^\\d{15}$",
                message = "L'ICE doit contenir exactement 15 chiffres")
        String ice,
        // Identifiant fiscal marocain : exactement 8 chiffres. Optionnel.
        @Pattern(regexp = "^$|^\\d{8}$",
                message = "L'identifiant fiscal doit contenir exactement 8 chiffres")
        String identifiantFiscal,
        // Champs particulier
        String prenom,
        String cin
) {
}

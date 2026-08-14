package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClientRequest(
        @NotBlank String nom,
        String prenom,
        @NotBlank @Email String email,
        // Un client peut avoir plusieurs telephones et plusieurs RIB.
        List<String> telephones,
        List<RibDto> ribs,
        String adresse,
        // Le plafond de credit n'est pas saisi ici : il se definit apres coup,
        // via l'endpoint dedie (POST /api/clients/{id}/plafond).
        @NotNull TypeClient typeClient,
        // Pas de commercialId : le commercial en charge est deduit de l'utilisateur
        // connecte a la creation, et n'est pas modifiable ensuite.
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
        String contactNom,
        String contactPrenom,
        // Champs particulier
        String dateNaissance,
        String cin
) {
}

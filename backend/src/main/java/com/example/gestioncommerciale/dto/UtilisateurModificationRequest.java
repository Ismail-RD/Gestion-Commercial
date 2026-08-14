package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Fiche d'un compte. Le mot de passe n'y figure pas : il appartient a son
 * titulaire, l'administrateur ne le choisit jamais a sa place.
 */
public record UtilisateurModificationRequest(
        @NotBlank String nom,
        @NotBlank String prenom,
        @NotBlank @Email String email,
        @NotNull Role role
) {
}

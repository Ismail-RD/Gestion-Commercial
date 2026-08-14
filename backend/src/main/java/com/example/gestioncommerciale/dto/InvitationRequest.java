package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Compte cree par l'administrateur : l'invite choisira son mot de passe. */
public record InvitationRequest(
        @NotBlank String nom,
        @NotBlank String prenom,
        @NotBlank @Email String email,
        @NotNull Role role
) {
}

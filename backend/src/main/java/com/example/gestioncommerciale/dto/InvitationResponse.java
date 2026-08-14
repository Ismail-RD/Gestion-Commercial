package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;

/** Ce que la page d'inscription affiche a l'invite avant sa saisie. */
public record InvitationResponse(
        String nom,
        String prenom,
        String email,
        Role role
) {
}

package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;

/**
 * Vue publique d'un utilisateur : jamais de mot de passe.
 */
public record UtilisateurResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        Role role,
        boolean actif
) {
}

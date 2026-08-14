package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;

public record AuthResponse(
        String token,
        Long id,
        String nom,
        String prenom,
        String email,
        Role role
) {
}

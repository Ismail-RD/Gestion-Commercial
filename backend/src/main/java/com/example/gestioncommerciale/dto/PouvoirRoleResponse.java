package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;

import java.math.BigDecimal;

public record PouvoirRoleResponse(
        Role role,
        BigDecimal seuilRemisePct,
        // Null pour un role qui ne fixe pas les plafonds de credit.
        BigDecimal plafondCreditMax
) {
}

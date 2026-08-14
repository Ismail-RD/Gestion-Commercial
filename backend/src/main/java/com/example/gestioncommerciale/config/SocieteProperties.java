package com.example.gestioncommerciale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Informations de la societe emettrice, imprimees sur les documents PDF.
 * En-tete (nom, coordonnees) : valeurs reelles ; mentions legales
 * (rc, patente, identifiantFiscal, cnss, ice) : a renseigner en configuration.
 */
@ConfigurationProperties(prefix = "app.societe")
public record SocieteProperties(
        String nom,
        String tagline,
        String adresse,
        String tel,
        String fax,
        String email,
        String siteWeb,
        String rc,
        String patente,
        String identifiantFiscal,
        String cnss,
        String ice
) {
}

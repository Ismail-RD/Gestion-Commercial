package com.example.gestioncommerciale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Mentions imprimees au bas du bon de livraison. */
@ConfigurationProperties(prefix = "app.bon-livraison")
public record BonLivraisonProperties(List<String> notes) {

    public BonLivraisonProperties {
        notes = notes != null ? notes : List.of();
    }
}

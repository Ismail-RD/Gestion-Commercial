package com.example.gestioncommerciale.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Coordonnees bancaires d'un tiers (client ou fournisseur). Type valeur possede
 * par le tiers : stocke en @ElementCollection, sans identite propre.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rib {

    private String rib;

    private String banque;
}

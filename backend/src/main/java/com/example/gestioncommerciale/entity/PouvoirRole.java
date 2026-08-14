package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ce qu'un role accorde sans en referer a plus haut.
 *
 * <p>Le seuil de remise sert deux fois : il decide si un document part en
 * attente de validation, et il borne ce que son titulaire peut lui-meme
 * valider. Le plafond de credit borne le credit qu'il consent a un client.
 * L'ADMIN n'a pas de ligne : il n'a pas de plafond.
 */
@Entity
@Table(name = "pouvoirs_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PouvoirRole {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    /** Remise maximale en pourcentage, borne incluse. */
    @Column(name = "seuil_remise_pct", nullable = false)
    private BigDecimal seuilRemisePct;

    /**
     * Plafond de credit maximal qu'il peut accorder a un client, en dirhams.
     * Null pour un role qui ne fixe pas les plafonds.
     */
    @Column(name = "plafond_credit_max")
    private BigDecimal plafondCreditMax;
}

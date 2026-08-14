package com.example.gestioncommerciale.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trace d'un mouvement de stock. La quantite est signee :
 * positive pour une entree, negative pour une sortie. La somme des mouvements
 * d'un couple (produit, depot) doit egaler le stock courant.
 */
@Entity
@Table(name = "mouvements_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TypeMouvement type;

    // Variation appliquee (signee : +entree / -sortie / delta pour ajustement)
    @NotNull
    private BigDecimal quantite;

    // Stock resultant apres application du mouvement (pour audit)
    @NotNull
    private BigDecimal quantiteApres;

    private String motif;

    @CreationTimestamp
    private LocalDateTime dateMouvement;

    // Utilisateur ayant effectue le mouvement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;
}

package com.example.gestioncommerciale.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Niveau de stock courant d'un produit dans un depot donne.
 * Unicite garantie sur le couple (produit, depot).
 */
@Entity
@Table(name = "stock_produits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"produit_id", "depot_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockProduit {

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
    @Builder.Default
    private BigDecimal quantite = BigDecimal.ZERO;

    /**
     * Part de {@link #quantite} promise a des commandes validees mais pas encore
     * livrees. Le stock physique ne sort qu'a la livraison ; ce qui est reserve
     * n'est donc plus disponible pour une autre commande.
     */
    @NotNull
    @Builder.Default
    private BigDecimal quantiteReservee = BigDecimal.ZERO;
}

package com.example.gestioncommerciale.entity;

import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

@Entity
@Table(name = "lignes_devis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneDevis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devis_id", nullable = false)
    private Devis devis;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    // Instantane du produit au moment du devis (immunise contre les modifs futures du catalogue)
    private String designation;

    @NotNull
    private BigDecimal quantite;

    @NotNull
    private BigDecimal prixUnitaire;

    private BigDecimal tauxTVA;

    // Remise en pourcentage (0 a 100)
    private BigDecimal remise;

    // Montant HT de la ligne, remise deduite
    private BigDecimal montantLigne;
}

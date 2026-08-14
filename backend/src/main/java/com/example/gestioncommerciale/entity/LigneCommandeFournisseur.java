package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
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
@Table(name = "lignes_commande_fournisseur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneCommandeFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_fournisseur_id", nullable = false)
    private CommandeFournisseur commandeFournisseur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    /**
     * Reference du produit chez le fournisseur, copiee a la saisie. Elle est
     * figee ici plutot que lue a l'affichage : le fournisseur peut renumeroter
     * son catalogue, le bon de commande deja emis, lui, ne change plus.
     */
    private String referenceFournisseur;

    private String designation;

    @NotNull
    @Column(precision = 15, scale = 2)
    private BigDecimal quantiteCommandee;

    /**
     * Quantite effectivement recue. Nulle tant que la marchandise n'est pas
     * arrivee ; un ecart avec la quantite commandee signale une casse, un
     * manquant ou un litige transporteur.
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal quantiteRecue;

    /** Prix unitaire dans la devise du fournisseur. */
    @Column(precision = 15, scale = 2)
    private BigDecimal prixUnitaireDevise;

    @Column(precision = 15, scale = 2)
    private BigDecimal montantDevise;

    /** Part des frais du dossier revenant a cette ligne, au prorata de sa valeur. */
    @Column(precision = 15, scale = 2)
    private BigDecimal quotePartFrais;

    /**
     * Cout de revient debarque d'une unite, en dirhams : prix converti plus
     * quote-part de frais. Fige a la reception — il vaut pour ce dossier, meme
     * si le cours et les frais changent ensuite.
     */
    // Nom explicite : Hibernate deriverait "cout_unitairemad" du sigle colle.
    @Column(name = "cout_unitaire_mad", precision = 15, scale = 4)
    private BigDecimal coutUnitaireMAD;
}

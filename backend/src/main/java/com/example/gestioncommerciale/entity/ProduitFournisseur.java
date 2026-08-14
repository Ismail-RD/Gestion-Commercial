package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "produits_fournisseurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProduitFournisseur {

    @EmbeddedId
    private ProduitFournisseurId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("produitId")
    @JoinColumn(name = "produit_id")
    private Produit produit;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("fournisseurId")
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    private String referenceFournisseur;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estPrincipal = false;
}

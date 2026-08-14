package com.example.gestioncommerciale.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "produits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String reference;

    @NotBlank
    private String designation;

    private String description;

    @NotNull
    private BigDecimal prixUnitaireHT;

    /**
     * Cout unitaire moyen pondere, recalcule a chaque reception fournisseur.
     *
     * <p>Null tant que le produit n'est jamais entre par une commande : on ne
     * devine pas ce qu'a coute un stock arrive par saisie manuelle. C'est ce
     * chiffre, et non {@link #prixUnitaireHT} qui est un prix de vente, qui
     * valorise le stock.
     */
    @Column(name = "cout_revient_moyen", precision = 15, scale = 4)
    private BigDecimal coutRevientMoyen;

    @NotNull
    private BigDecimal tauxTVA;

    private String uniteMesure;

    // Chemin relatif de la fiche technique sur le systeme de fichiers (null si absente).
    private String ficheTechnique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    // Pas de cascade : Marque et Fournisseur sont des entites independantes,
    // gerees par leurs propres services. Cascader PERSIST ferait echouer la
    // sauvegarde d'un produit referencant des marques deja en base (detached).
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "produits_marques",
        joinColumns = @JoinColumn(name = "produit_id"),
        inverseJoinColumns = @JoinColumn(name = "marque_id")
    )
    @Builder.Default
    private Set<Marque> marques = new HashSet<>();

    // Liaison porteuse d'attributs (reference chez le fournisseur, fournisseur principal).
    // Le produit possede ses liaisons : cascade + orphanRemoval.
    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProduitFournisseur> fournisseurs = new HashSet<>();

    public void ajouterFournisseur(Fournisseur fournisseur, String referenceFournisseur, boolean estPrincipal) {
        ProduitFournisseur lien = ProduitFournisseur.builder()
                .id(new ProduitFournisseurId())
                .produit(this)
                .fournisseur(fournisseur)
                .referenceFournisseur(referenceFournisseur)
                .estPrincipal(estPrincipal)
                .build();
        this.fournisseurs.add(lien);
    }

}

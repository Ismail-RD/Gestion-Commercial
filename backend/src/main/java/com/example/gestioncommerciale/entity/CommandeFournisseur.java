package com.example.gestioncommerciale.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Commande passee a un fournisseur, du bon de commande a l'entree en stock.
 *
 * <p>Une seule entite pour le document et le dossier : le bon de commande, le
 * suivi du transit et la reception portent le meme fournisseur et les memes
 * lignes. Les separer obligerait a les tenir synchronises pour rien.
 *
 * <p>Le bloc import (devise, incoterm, transport) reste vide pour un achat
 * local : c'est la meme commande, sans traversee de frontiere.
 */
@Entity
@Table(name = "commandes_fournisseur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandeFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StatutCommandeFournisseur statut;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    /** Depot ou la marchandise entrera a la reception. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_reception_id", nullable = false)
    private Depot depotReception;

    /** Auteur de la commande : celui qui l'a passee au fournisseur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acheteur_id")
    private Utilisateur acheteur;

    @CreationTimestamp
    private LocalDateTime dateCreation;

    /** Date d'emission du bon de commande, posee au passage en COMMANDEE. */
    private LocalDate dateCommande;

    private LocalDate dateArriveePrevue;

    /**
     * Depart de la marchandise. C'est d'elle que se mesure le delai reel du
     * fournisseur, celui qu'il tient ou non, par opposition au delai annonce.
     */
    @Column(name = "date_transit")
    private LocalDate dateTransit;

    /**
     * Arrivee au port ou a l'aeroport. Le magasinage et les surestaries courent
     * a partir de la : c'est la date qui coute de l'argent quand on tarde.
     */
    @Column(name = "date_douane")
    private LocalDate dateDouane;

    /** Premiere livraison, meme partielle. */
    @Column(name = "date_premiere_reception")
    private LocalDate datePremiereReception;

    /** Reception complete : le dossier est solde, plus rien n'est attendu. */
    private LocalDate dateReception;

    @Column(name = "date_annulation")
    private LocalDate dateAnnulation;

    // --- Bloc import, vide pour un achat local ---

    /**
     * Devise de facturation du fournisseur. MAD pour un achat local, auquel cas
     * le taux vaut 1.
     */
    private String devise;

    /**
     * Taux de conversion vers le dirham, fige a la commande. Le cours bouge
     * entre la commande et le paiement : ce qui compte pour le cout de revient,
     * c'est celui qui a ete retenu.
     */
    @Column(precision = 12, scale = 6)
    private BigDecimal tauxChange;

    @Enumerated(EnumType.STRING)
    private Incoterm incoterm;

    @Enumerated(EnumType.STRING)
    private ModeTransport modeTransport;

    /**
     * Provenance de la marchandise. Elle determine les droits de douane et le
     * certificat d'origine exige au dedouanement.
     *
     * <p>Porte sur le dossier et non sur la ligne : deux produits d'un meme
     * conteneur peuvent theoriquement venir de pays differents, mais l'exiger
     * par ligne donnerait un champ que personne ne remplirait.
     */
    private String paysOrigine;

    private String transporteur;

    /** Numero de conteneur ou de connaissement, repere du suivi. */
    private String referenceTransport;

    private String portArrivee;

    /** Total des lignes, dans la devise du fournisseur. */
    @Column(precision = 15, scale = 2)
    private BigDecimal montantDevise;

    // --- Frais du dossier, en dirhams ---
    //
    // Douane et transit sont factures localement, en dirhams. Un fret paye en
    // devise se convertit au meme taux avant d'etre saisi ici : le dossier ne
    // retient qu'un seul cours, celui qui a ete pratique.

    @Column(precision = 15, scale = 2)
    private BigDecimal fraisFret;

    @Column(precision = 15, scale = 2)
    private BigDecimal fraisAssurance;

    @Column(precision = 15, scale = 2)
    private BigDecimal droitsDouane;

    /** Transit, manutention, magasinage portuaire. */
    @Column(precision = 15, scale = 2)
    private BigDecimal fraisTransit;

    /**
     * Vrai si fret et assurance sont libelles dans la devise du dossier. Le
     * transitaire etranger facture souvent en euros, alors que douane et transit
     * sont toujours percus en dirhams : une devise unique pour les quatre serait
     * fausse dans les deux sens.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean fraisTransportEnDevise = false;

    /** Somme des frais du dossier en dirhams, zero si aucun n'est renseigne. */
    public BigDecimal totalFrais() {
        BigDecimal transport = valeur(fraisFret).add(valeur(fraisAssurance));
        if (fraisTransportEnDevise) {
            transport = transport.multiply(valeur(tauxChange, BigDecimal.ONE));
        }
        return transport.add(valeur(droitsDouane)).add(valeur(fraisTransit));
    }

    private BigDecimal valeur(BigDecimal v) {
        return valeur(v, BigDecimal.ZERO);
    }

    private BigDecimal valeur(BigDecimal v, BigDecimal defaut) {
        return v != null ? v : defaut;
    }

    private String observations;

    @OneToMany(mappedBy = "commandeFournisseur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneCommandeFournisseur> lignes = new ArrayList<>();

    public void ajouterLigne(LigneCommandeFournisseur ligne) {
        ligne.setCommandeFournisseur(this);
        this.lignes.add(ligne);
    }

    public void viderLignes() {
        this.lignes.clear();
    }
}

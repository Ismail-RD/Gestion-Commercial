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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commandes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero;

    @CreationTimestamp
    private LocalDateTime dateCommande;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StatutCommande statut;

    // --- Dates des etapes franchies ---
    // Le statut ne dit que l'etat present. Ces dates gardent le chemin parcouru :
    // c'est d'elles que se deduisent le delai de preparation et le respect du
    // delai de livraison promis au client.

    /** Validation : le stock passe de disponible a reserve. */
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    /** Moment ou l'encadrement a leve le blocage sur la remise. */
    @Column(name = "date_validation_remise")
    private LocalDateTime dateValidationRemise;

    /** Prise en charge par l'entrepot. */
    @Column(name = "date_en_preparation")
    private LocalDateTime dateEnPreparation;

    /** Depart reel de la marchandise : la reservation devient une sortie. */
    @Column(name = "date_livraison")
    private LocalDateTime dateLivraison;

    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;

    private BigDecimal montantHT;

    private BigDecimal montantTTC;

    // Devis d'origine (une commande peut aussi etre creee sans devis)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false)
    private Utilisateur commercial;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneCommande> lignes = new ArrayList<>();

    public void ajouterLigne(LigneCommande ligne) {
        ligne.setCommande(this);
        this.lignes.add(ligne);
    }

    public void viderLignes() {
        this.lignes.clear();
    }
}

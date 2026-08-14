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

@Entity
@Table(name = "devis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero;

    // Reference libre (ex. reference dossier/affaire ou demande client). Optionnelle.
    private String reference;

    @CreationTimestamp
    private LocalDateTime dateCreation;

    @NotNull
    private LocalDate dateValidite;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StatutDevis statut;

    private BigDecimal montantHT;

    private BigDecimal montantTTC;

    /**
     * Aval de l'encadrement sur une remise au-dela du seuil. Ne libere que
     * l'action : le devis reste au brouillon, c'est au commercial de l'envoyer.
     * Toute reprise des lignes le remet a faux, un accord ne vaut que pour la
     * remise sur laquelle il a porte.
     */
    @Column(nullable = false)
    private boolean remiseValidee;

    // --- Dates des etapes franchies ---
    // Un statut dit ou en est le devis, jamais depuis quand. Ces dates comblent
    // le manque : elles restent posees meme si le devis avance ensuite.

    /** Passage a ENVOYE : le prix est desormais opposable au client. */
    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    /** Moment ou l'encadrement a tranche sur la remise (accord ou refus). */
    @Column(name = "date_validation_remise")
    private LocalDateTime dateValidationRemise;

    // Renseignés uniquement lorsque le client accepte ou refuse le devis
    private LocalDateTime dateReponseClient;

    private String commentaireClient;

    // --- Envoi au client par email ---

    // Jeton unique du lien personnel envoye au client (non devinable).
    @Column(unique = true)
    private String tokenClient;

    private LocalDateTime dateEnvoiEmail;

    /**
     * Reponse donnee par le client via son lien. Purement informative : elle ne
     * modifie pas {@link #statut}, la validation restant manuelle.
     */
    @Enumerated(EnumType.STRING)
    private ReponseClient reponseClient;

    // Chemin relatif du bon de commande depose par le client a l'acceptation.
    private String bonCommande;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false)
    private Utilisateur commercial;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneDevis> lignes = new ArrayList<>();

    public void ajouterLigne(LigneDevis ligne) {
        ligne.setDevis(this);
        this.lignes.add(ligne);
    }

    public void viderLignes() {
        this.lignes.clear();
    }
}

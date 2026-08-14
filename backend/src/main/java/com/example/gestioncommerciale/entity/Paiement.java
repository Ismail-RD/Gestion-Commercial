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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facture_id", nullable = false)
    private Facture facture;

    @CreationTimestamp
    private LocalDateTime datePaiement;

    @NotNull
    private BigDecimal montant;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ModePaiement modePaiement;

    // Reference du paiement (n de cheque, id de transaction...)
    private String reference;

    /**
     * Etat du reglement. Seul {@link StatutPaiement#ENCAISSE} compte dans le
     * montant paye d'une facture : un cheque dans le tiroir n'a rien solde.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutPaiement statut = StatutPaiement.ENCAISSE;

    // --- Effet de commerce : nul pour especes, carte et virement ---

    /** Numero du cheque ou de la traite. */
    private String numeroEffet;

    private String banqueEmettrice;

    /**
     * Date portee sur l'effet par celui qui l'etablit. Elle differe de la date
     * ou l'entreprise le recoit, et c'est elle qui fait foi en cas de litige.
     */
    private LocalDate dateEmission;

    /** Date a laquelle l'effet a ete remis par le client. */
    private LocalDate dateReception;

    /**
     * Date a laquelle l'effet devient payable. C'est elle qui porte la
     * prevision de tresorerie : un cheque post-date est la norme.
     */
    private LocalDate dateEcheance;

    /** Remise en banque pour encaissement. */
    private LocalDate dateRemise;

    /** Credit effectif du compte. */
    private LocalDate dateEncaissement;

    private String motifRejet;

    /** Un effet de commerce traverse un cycle ; les autres modes sont immediats. */
    public boolean estUnEffet() {
        return modePaiement == ModePaiement.CHEQUE || modePaiement == ModePaiement.TRAITE;
    }
}

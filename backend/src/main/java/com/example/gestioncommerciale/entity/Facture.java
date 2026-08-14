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
@Table(name = "factures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero;

    @CreationTimestamp
    private LocalDateTime dateFacture;

    @NotNull
    private LocalDate dateEcheance;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StatutFacture statut;

    private BigDecimal montantHT;

    private BigDecimal montantTTC;

    @Builder.Default
    private BigDecimal montantPaye = BigDecimal.ZERO;

    // Date du dernier envoi de la facture au client par email (null si jamais envoyee).
    private LocalDateTime dateEnvoiEmail;

    /**
     * Jour ou la facture a ete soldee. Elle s'efface si un reglement est ensuite
     * rejete ou supprime : une facture qui redevient due ne peut pas garder une
     * date de reglement.
     */
    @Column(name = "date_reglement")
    private LocalDate dateReglement;

    /** Une commande ne porte qu'une facture (index unique en base). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", unique = true)
    private Commande commande;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @OneToMany(mappedBy = "facture", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneFacture> lignes = new ArrayList<>();

    public void ajouterLigne(LigneFacture ligne) {
        ligne.setFacture(this);
        this.lignes.add(ligne);
    }

    /**
     * Recalcule le statut a partir des seules donnees de la facture.
     *
     * <p>La regle vit ici, et non dans les services, parce qu'elle depend de
     * trois champs qui bougent a trois endroits differents : l'encaissement d'un
     * paiement, la renegociation de l'echeance, et le simple passage du temps.
     * Dispersee, elle finirait par diverger et laisserait des factures marquees
     * en retard alors qu'elles ne le sont plus.
     *
     * <p>Deux choix de priorite. ANNULEE est une decision humaine, pas un etat
     * deduit : elle n'est jamais ecrasee. Et le retard prime sur le paiement
     * partiel, parce que c'est lui qui appelle une relance ; le montant deja
     * regle reste lisible dans {@code montantPaye}.
     *
     * @return true si le statut a change
     */
    public boolean recalculerStatut() {
        if (statut == StatutFacture.ANNULEE) {
            return false;
        }
        StatutFacture avant = statut;
        BigDecimal total = montantTTC != null ? montantTTC : BigDecimal.ZERO;
        BigDecimal paye = montantPaye != null ? montantPaye : BigDecimal.ZERO;

        if (paye.compareTo(total) >= 0) {
            statut = StatutFacture.PAYEE;
        } else if (dateEcheance != null && dateEcheance.isBefore(LocalDate.now())) {
            statut = StatutFacture.EN_RETARD;
        } else if (paye.signum() > 0) {
            statut = StatutFacture.PARTIELLEMENT_PAYEE;
        } else {
            statut = StatutFacture.EMISE;
        }

        // La date de reglement suit le statut : posee au solde, retiree si un
        // rejet ou une suppression de paiement rend la facture a nouveau due.
        if (statut == StatutFacture.PAYEE) {
            if (dateReglement == null) {
                dateReglement = LocalDate.now();
            }
        } else {
            dateReglement = null;
        }
        return statut != avant;
    }
}

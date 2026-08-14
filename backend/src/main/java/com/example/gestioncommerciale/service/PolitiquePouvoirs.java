package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.LigneCommande;
import com.example.gestioncommerciale.entity.LigneDevis;
import com.example.gestioncommerciale.entity.PouvoirRole;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutCommande;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.PouvoirRoleRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Ce que chaque role engage seul : remise consentie et credit accorde.
 *
 * <p>Regroupe ici plutot que dispersee dans chaque service : le workflow,
 * l'impression et l'envoi au client doivent appliquer exactement la meme regle,
 * sinon un document part chez le client par une porte que les autres gardent
 * fermee.
 *
 * <p>Le seuil de remise borne aussi ce que son titulaire valide chez autrui :
 * un responsable commercial ne couvre pas une remise superieure a la sienne,
 * elle remonte a l'administrateur, seul a n'avoir aucun plafond.
 */
@Component
public class PolitiquePouvoirs {

    private final PouvoirRoleRepository pouvoirRepository;

    public PolitiquePouvoirs(PouvoirRoleRepository pouvoirRepository) {
        this.pouvoirRepository = pouvoirRepository;
    }

    /**
     * Seuil de remise applicable a cet utilisateur, ou {@code null} s'il n'a pas
     * de plafond. L'administrateur est le dernier recours : rien ne le depasse.
     */
    public BigDecimal seuilDe(Utilisateur utilisateur) {
        return pouvoir(utilisateur)
                .map(PouvoirRole::getSeuilRemisePct)
                .orElseGet(() -> sansPouvoir(utilisateur) ? null : BigDecimal.ZERO);
    }

    /**
     * Plafond de credit maximal que cet utilisateur peut accorder a un client,
     * ou {@code null} s'il n'est pas borne.
     */
    public BigDecimal plafondCreditMaxDe(Utilisateur utilisateur) {
        return pouvoir(utilisateur)
                .map(PouvoirRole::getPlafondCreditMax)
                .orElse(null);
    }

    /** Vrai si le credit demande excede ce que cet utilisateur accorde seul. */
    public boolean depasseSonPlafondCredit(Utilisateur utilisateur, BigDecimal credit) {
        BigDecimal max = plafondCreditMaxDe(utilisateur);
        return max != null && credit != null && credit.compareTo(max) > 0;
    }

    private Optional<PouvoirRole> pouvoir(Utilisateur utilisateur) {
        if (sansPouvoir(utilisateur)) {
            return Optional.empty();
        }
        Role role = utilisateur.getRole() == Role.RESPONSABLE_COMMERCIAL
                ? Role.RESPONSABLE_COMMERCIAL : Role.COMMERCIAL;
        return pouvoirRepository.findById(role);
    }

    /** L'administrateur n'est borne par rien, et un utilisateur absent non plus. */
    private boolean sansPouvoir(Utilisateur utilisateur) {
        return utilisateur == null || utilisateur.getRole() == Role.ADMIN;
    }

    /** Remise la plus forte portee par une ligne du devis. */
    public BigDecimal remiseMax(Devis devis) {
        return remiseMax(devis.getLignes().stream().map(LigneDevis::getRemise));
    }

    /** Remise la plus forte portee par une ligne de la commande. */
    public BigDecimal remiseMax(Commande commande) {
        return remiseMax(commande.getLignes().stream().map(LigneCommande::getRemise));
    }

    private BigDecimal remiseMax(Stream<BigDecimal> remises) {
        return remises.filter(r -> r != null).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Vrai si cette remise excede ce que l'utilisateur accorde ou valide seul. */
    public boolean depassePouvoirDe(Utilisateur utilisateur, BigDecimal remise) {
        BigDecimal seuil = seuilDe(utilisateur);
        return seuil != null && remise.compareTo(seuil) > 0;
    }

    public boolean depassePouvoirDe(Utilisateur utilisateur, Devis devis) {
        return depassePouvoirDe(utilisateur, remiseMax(devis));
    }

    public boolean depassePouvoirDe(Utilisateur utilisateur, Commande commande) {
        return depassePouvoirDe(utilisateur, remiseMax(commande));
    }

    /**
     * Vrai si la remise du devis attend encore un aval. On ne peut pas le lire
     * dans le statut : une fois l'accord donne le devis retourne au brouillon,
     * c'est au commercial de l'envoyer. Seul l'accord memorise fait foi, et il
     * tombe des que les lignes changent.
     */
    public boolean validationAttendue(Devis devis) {
        return !devis.isRemiseValidee();
    }

    /**
     * Pour une commande le statut suffit : la remise excessive l'y place des la
     * saisie, et la validation de l'encadrement l'en sort.
     */
    public boolean validationAttendue(Commande commande) {
        return commande.getStatut() == StatutCommande.EN_ATTENTE_VALIDATION;
    }
}

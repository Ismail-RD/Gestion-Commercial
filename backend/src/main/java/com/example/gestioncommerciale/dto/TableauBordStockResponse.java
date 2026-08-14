package com.example.gestioncommerciale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vision d'ensemble du stock, orientee decision.
 *
 * <p>Tout se deduit des donnees existantes : aucun seuil a parametrer, aucune
 * prevision. Chaque bloc repond a une question qu'on se pose devant un
 * entrepot : combien d'argent y dort, qu'est-ce qui manque, qu'est-ce qui ne
 * bouge plus, et qui a touche aux quantites.
 */
public record TableauBordStockResponse(
        /** Fenetre d'observation des mouvements, en jours. */
        int jours,
        Valeur valeur,
        List<ValeurParDepot> parDepot,
        List<ValeurParCategorie> parCategorie,
        Compteurs compteurs,
        List<LigneManquante> ruptures,
        List<TransfertPossible> transferts,
        List<LigneDormante> dormants,
        List<LigneRotation> rotations,
        Flux flux,
        List<LigneAjustement> derniersAjustements
) {

    /**
     * Argent immobilise. La valeur totale est celle du prix de vente : c'est ce
     * que le stock pourrait rapporter, pas ce qu'il a coute. Le reserve est deja
     * promis a des commandes validees, en stock sans etre vendable.
     *
     * <p>{@code auCoutDeRevient} est la vraie valeur comptable, mais elle ne
     * couvre que les references dont une reception fournisseur a etabli le
     * cout : {@code referencesSansCout} dit combien y echappent encore, pour
     * qu'on ne prenne pas un total partiel pour un total.
     */
    public record Valeur(
            BigDecimal totale,
            BigDecimal reservee,
            BigDecimal disponible,
            BigDecimal auCoutDeRevient,
            /** Valeur de vente des seules references dont le cout est connu. */
            BigDecimal venteDesReferencesChiffrees,
            int referencesSansCout
    ) {
    }

    public record ValeurParDepot(
            String depotCode,
            BigDecimal valeur,
            BigDecimal quantite,
            BigDecimal quantiteReservee
    ) {
    }

    public record ValeurParCategorie(
            String categorie,
            BigDecimal valeur
    ) {
    }

    public record Compteurs(
            /** References presentes au catalogue. */
            int references,
            /** References avec au moins une ligne de stock. */
            int referencesEnStock,
            /** Jamais entrees en stock : ni rupture ni disponible, simplement absentes. */
            int jamaisEntrees,
            /** Disponible a zero ou negatif, tous depots confondus. */
            int ruptures,
            /** Marchandise presente mais entierement promise a des commandes. */
            int toutReserve,
            /** En stock sans aucune sortie sur la fenetre. */
            int dormants,
            int depots
    ) {
    }

    /** Produit dont il ne reste rien de vendable. */
    public record LigneManquante(
            Long produitId,
            String reference,
            String designation,
            BigDecimal quantite,
            BigDecimal quantiteReservee,
            BigDecimal disponible,
            /** true si la marchandise est la, mais entierement reservee. */
            boolean toutReserve
    ) {
    }

    /**
     * Un depot est a sec pendant qu'un autre a de quoi servir : le transfert se
     * decide tout de suite, sans attendre de reapprovisionnement.
     */
    public record TransfertPossible(
            Long produitId,
            String reference,
            String designation,
            String depotDemandeur,
            String depotFournisseur,
            BigDecimal disponibleChezFournisseur
    ) {
    }

    /** Marchandise en stock qui n'est pas sortie une seule fois sur la fenetre. */
    public record LigneDormante(
            Long produitId,
            String reference,
            String designation,
            BigDecimal quantite,
            BigDecimal valeurImmobilisee,
            /** Jours depuis la derniere sortie, null si aucune sortie connue. */
            Long joursDepuisDerniereSortie
    ) {
    }

    public record LigneRotation(
            Long produitId,
            String reference,
            String designation,
            BigDecimal quantiteSortie
    ) {
    }

    /**
     * Ce qui est entre, sorti et corrige sur la fenetre. Entrees et sorties sont
     * des volumes, toujours positifs ; les ajustements gardent leur signe, la
     * correction nette disant si l'inventaire a trouve plus ou moins que prevu.
     */
    public record Flux(
            BigDecimal entrees,
            BigDecimal sorties,
            BigDecimal ajustements,
            int nombreMouvements
    ) {
    }

    /**
     * Les corrections d'inventaire meritent d'etre vues : repetees sur un meme
     * produit, elles signalent une casse, un vol ou un comptage douteux.
     */
    public record LigneAjustement(
            LocalDateTime date,
            String reference,
            String designation,
            String depotCode,
            BigDecimal quantite,
            String motif,
            String utilisateur
    ) {
    }
}

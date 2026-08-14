package com.example.gestioncommerciale.security;

/**
 * Matrice des droits, regroupee ici pour se lire d'un coup d'oeil plutot que
 * dispersee en chaines dans les controleurs.
 *
 * <p>L'ADMIN figure partout. Le COMMERCIAL, lui, est en plus cantonne a ses
 * propres dossiers : ces expressions ouvrent l'acces au type de ressource, la
 * restriction au portefeuille est appliquee dans les services.
 */
public final class Autorisations {

    private Autorisations() {
    }

    // --- Commercial : clients, devis, commandes ---

    /**
     * Consultation des clients et documents commerciaux. Le COMPTABLE en fait
     * partie : il ne peut pas facturer une commande sans la lire, ni retrouver
     * le devis ou le client derriere une facture.
     */
    public static final String LIRE_COMMERCIAL =
            "hasAnyRole('ADMIN','RESPONSABLE_COMMERCIAL','MAGASINIER','COMMERCIAL','COMPTABLE')";

    /** Creation et modification des clients, devis et commandes. */
    public static final String ECRIRE_COMMERCIAL =
            "hasAnyRole('ADMIN','RESPONSABLE_COMMERCIAL','COMMERCIAL')";

    /** Encadrement : validation des remises, plafonds, blocage, reattribution. */
    public static final String ENCADREMENT_COMMERCIAL =
            "hasAnyRole('ADMIN','RESPONSABLE_COMMERCIAL')";

    // --- Logistique ---

    /** Mouvements de stock et gestion des depots. */
    public static final String ECRIRE_STOCK = "hasAnyRole('ADMIN','MAGASINIER')";

    /**
     * Traitement des commandes acceptees : validation avec prise de depot,
     * preparation, livraison. Metier du magasinier ; le responsable commercial
     * en est exclu, il pilote la vente et non l'entrepot.
     */
    public static final String TRAITER_COMMANDE = "hasAnyRole('ADMIN','MAGASINIER')";

    // --- Catalogue ---

    /** Produits, marques et fournisseurs. */
    public static final String ECRIRE_CATALOGUE = "hasAnyRole('ADMIN','RESPONSABLE_IMPORT')";

    /** Categories : structure du catalogue, reservee a l'admin. */
    public static final String ECRIRE_CATEGORIE = "hasRole('ADMIN')";

    /** Fournisseurs : hors du champ commercial et logistique. */
    public static final String ACCES_FOURNISSEUR = "hasAnyRole('ADMIN','RESPONSABLE_IMPORT')";

    // --- Facturation ---

    /** Emission, modification, envoi et encaissement des factures. */
    public static final String ECRIRE_FACTURE = "hasAnyRole('ADMIN','COMPTABLE')";

    // --- Lectures ouvertes ---

    /**
     * Catalogue et stock : consultes par tous les roles, chacun en ayant besoin
     * pour son travail (le magasinier doit voir les produits qu'il stocke, le
     * commercial ceux qu'il vend).
     */
    public static final String LIRE_REFERENTIEL = "isAuthenticated()";

    /** Administration des comptes. */
    public static final String ADMINISTRER = "hasRole('ADMIN')";
}

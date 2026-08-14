package com.example.gestioncommerciale.entity;

/**
 * Cycle d'une commande passee a un fournisseur.
 *
 * <p>Les trois etats intermediaires ne concernent que les achats a l'import :
 * un achat local passe directement de COMMANDEE a RECEPTIONNEE.
 */
public enum StatutCommandeFournisseur {
    BROUILLON,
    /** Le bon de commande est emis : les lignes ne bougent plus. */
    COMMANDEE,
    EN_TRANSIT,
    EN_DOUANE,
    /**
     * Une partie seulement est arrivee. Le dossier reste ouvert : le reliquat
     * peut encore etre receptionne, sans quoi la marchandise manquante
     * n entrerait jamais en stock meme livree plus tard.
     */
    RECEPTIONNEE_PARTIELLEMENT,
    /** Toute la marchandise commandee est entree en stock. */
    RECEPTIONNEE,
    ANNULEE
}

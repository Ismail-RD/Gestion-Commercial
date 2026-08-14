package com.example.gestioncommerciale.entity;

/**
 * Nature de l'evenement notifie. Sert au regroupement, au filtrage et surtout
 * a l'idempotence : les alertes recurrentes (echeances, retards) se dedupliquent
 * sur le couple type + cle.
 */
public enum TypeNotification {

    // --- Ce qui attend une decision ---
    REMISE_A_VALIDER,
    REMISE_VALIDEE,
    REMISE_REFUSEE,

    // --- Ce qui passe la main a un autre metier ---
    COMMANDE_A_PREPARER,
    COMMANDE_A_FACTURER,

    // --- Ce que le client repond ---
    DEVIS_ACCEPTE,
    DEVIS_REFUSE,

    // --- L'argent ---
    PAIEMENT_REJETE,
    CLIENT_BLOQUE,

    // --- Ce que seul le temps declenche ---
    // Aucune action humaine ne les provoque : elles naissent d'une date qui
    // passe, et sans balayage personne ne les verrait venir.
    FACTURE_ECHUE,
    EFFET_A_REMETTRE,
    DEVIS_EXPIRE_BIENTOT,
    IMPORT_EN_RETARD
}

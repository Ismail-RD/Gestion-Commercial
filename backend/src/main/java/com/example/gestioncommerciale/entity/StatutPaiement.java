package com.example.gestioncommerciale.entity;

/**
 * Ou en est un reglement.
 *
 * <p>Especes, carte et virement naissent {@link #ENCAISSE} : l'argent est la.
 * Un effet de commerce, lui, traverse les trois autres etats avant que le
 * compte soit credite — et seul {@link #ENCAISSE} compte dans le montant paye
 * d'une facture.
 */
public enum StatutPaiement {
    /** Effet recu du client, encore dans le tiroir. */
    RECU,
    /** Remis a la banque pour encaissement. */
    DEPOSE,
    /** Fonds credites : c'est le seul etat qui solde une facture. */
    ENCAISSE,
    /** Revenu impaye : le montant retombe, et la facture avec. */
    REJETE
}

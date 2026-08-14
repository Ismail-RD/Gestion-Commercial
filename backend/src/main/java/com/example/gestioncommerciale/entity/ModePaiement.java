package com.example.gestioncommerciale.entity;

/**
 * Moyens de reglement acceptes.
 *
 * <p>Le cheque et la traite sont des effets de commerce : ils passent par un
 * cycle (recu, remis, encaisse) avant que l argent arrive, contrairement aux
 * trois autres qui sont immediats.
 */
public enum ModePaiement {
    VIREMENT,
    CHEQUE,
    /** Lettre de change : meme cycle que le cheque, avec une echeance plus lointaine. */
    TRAITE,
    CARTE,
    ESPECES
}

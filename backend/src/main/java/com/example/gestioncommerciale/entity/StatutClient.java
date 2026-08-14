package com.example.gestioncommerciale.entity;

/**
 * Etat d'un client vis-a-vis du credit.
 * BLOQUE : son encours a depasse son plafond ; il ne peut plus passer de
 * commande tant qu'un administrateur ne l'a pas debloque.
 */
public enum StatutClient {
    ACTIF,
    BLOQUE
}

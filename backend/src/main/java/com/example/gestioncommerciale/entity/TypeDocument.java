package com.example.gestioncommerciale.entity;

/**
 * Document vers lequel une notification renvoie. Le lien n'est pas une cle
 * etrangere mais un couple (type, identifiant) : une notification doit survivre
 * a la suppression de son document, sinon effacer un devis effacerait aussi la
 * trace de l'alerte qu'il avait declenchee.
 */
public enum TypeDocument {
    DEVIS,
    COMMANDE,
    COMMANDE_FOURNISSEUR,
    FACTURE,
    CLIENT
}

package com.example.gestioncommerciale.entity;

/**
 * Urgence d'une notification, qui commande sa couleur et son rang d'affichage.
 *
 * <p>Trois niveaux suffisent, et il faut s'y tenir : une liste ou tout est
 * urgent ne hierarchise plus rien.
 */
public enum NiveauNotification {

    /** Pour information : le destinataire n'a rien a faire. */
    INFORMATION,

    /** Une action lui revient, sans caractere d'urgence immediat. */
    ALERTE,

    /** De l'argent ou un engagement client est en jeu maintenant. */
    URGENT
}

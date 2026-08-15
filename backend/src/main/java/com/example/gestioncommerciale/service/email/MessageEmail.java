package com.example.gestioncommerciale.service.email;

import java.util.List;

/**
 * Un email pret a partir, independant du moyen d'acheminement.
 *
 * <p>Le corps est deja rendu : le service metier sait ce qu'il veut dire, la
 * passerelle sait comment l'envoyer, et aucun des deux n'a besoin de connaitre
 * le travail de l'autre.
 */
public record MessageEmail(
        String destinataire,
        String sujet,
        String html,
        List<PieceJointe> piecesJointes
) {

    /** Message sans piece jointe : le cas de l'invitation. */
    public MessageEmail(String destinataire, String sujet, String html) {
        this(destinataire, sujet, html, List.of());
    }

    public record PieceJointe(String nom, byte[] contenu, String typeMime) {

        /** Le seul type que l'application attache aujourd'hui. */
        public static PieceJointe pdf(String nom, byte[] contenu) {
            return new PieceJointe(nom, contenu, "application/pdf");
        }
    }
}

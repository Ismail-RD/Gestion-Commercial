package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.Role;

import java.util.List;

/**
 * Tableau de bord d'un utilisateur, faconne par son role.
 *
 * <p>La forme est volontairement generique : quelques chiffres, puis des files
 * d'attente. Six roles pour une seule structure, donc un seul ecran a maintenir,
 * et surtout la meme promesse partout : ce qu'on voit en arrivant, c'est ce
 * qu'on a a faire.
 */
public record TableauBordResponse(
        Role role,
        String titre,
        String sousTitre,
        List<Indicateur> indicateurs,
        List<FileAttente> files,
        Visuel visuel
) {

    /**
     * Une lecture graphique, en barres comparees. Une seule forme pour six
     * usages : serie mensuelle, classement, repartition. La part est calculee
     * ici et non a l'affichage, pour que l'ecran n'ait pas a savoir ce qu'il
     * dessine.
     */
    public record Visuel(
            String titre,
            String description,
            /** Dit a l'ecran quel graphique dessiner ; les donnees, elles, ne changent pas. */
            FormeVisuel forme,
            List<Barre> barres
    ) {
    }

    /**
     * Trois lectures possibles d'une meme suite de valeurs. Le serveur choisit,
     * parce que lui seul sait si les libelles sont des mois, des noms ou des
     * tranches : l'ecran ne peut pas le deviner sans se tromper un jour.
     */
    public enum FormeVisuel {
        /** Suite chronologique : l'evolution compte plus que les valeurs isolees. */
        SERIE,
        /** Comparaison d'elements sans ordre naturel : le classement compte. */
        CLASSEMENT,
        /** Parts d'un tout : c'est le poids relatif qui se lit. */
        REPARTITION
    }

    /** @param part longueur relative, de 0 a 100 */
    public record Barre(
            String libelle,
            String valeur,
            String detail,
            double part
    ) {
    }

    /**
     * Un chiffre a lire d'un coup d'oeil.
     *
     * @param ton neutre, succes, attention ou alerte : dicte la couleur, pas le sens
     */
    public record Indicateur(
            String libelle,
            String valeur,
            String detail,
            String ton
    ) {
    }

    /**
     * Une liste de choses a traiter. {@code total} peut depasser le nombre
     * d'elements montres : on affiche les premiers, le lien mene au reste.
     */
    public record FileAttente(
            String titre,
            String description,
            String lien,
            int total,
            List<Element> elements
    ) {
    }

    public record Element(
            String titre,
            String sousTitre,
            String info,
            String lien,
            String ton
    ) {
    }
}

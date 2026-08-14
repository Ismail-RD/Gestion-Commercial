package com.example.gestioncommerciale.service;

/**
 * Numerotation des documents (devis, commandes, factures).
 *
 * <p>Le numero suivant se deduit du plus haut deja attribue, jamais du nombre
 * de lignes en base : une suppression ferait retomber le compteur sur un numero
 * deja pris, et l'insertion echouerait sur la contrainte d'unicite. Les trous
 * laisses par un document supprime restent des trous, ce qui est le
 * comportement attendu d'une numerotation de piece.
 */
public final class NumeroDocument {

    private NumeroDocument() {
    }

    /**
     * @param prefix  prefixe complet, par exemple {@code "FAC-2026-"}
     * @param dernier plus haut numero deja attribue pour ce prefixe, ou null
     */
    public static String suivant(String prefix, String dernier) {
        long compteur = 1;
        if (dernier != null && dernier.length() > prefix.length()) {
            try {
                compteur = Long.parseLong(dernier.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                // Numero saisi hors format : on repart de 1, la contrainte
                // d'unicite reste le dernier garde-fou.
                compteur = 1;
            }
        }
        return prefix + String.format("%04d", compteur);
    }
}

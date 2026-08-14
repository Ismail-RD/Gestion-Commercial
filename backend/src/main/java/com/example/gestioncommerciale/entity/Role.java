package com.example.gestioncommerciale.entity;

/**
 * Roles de l'application.
 *
 * <ul>
 *   <li>{@link #ADMIN} : acces complet.</li>
 *   <li>{@link #RESPONSABLE_COMMERCIAL} : pilote la force de vente (clients,
 *       devis et commandes de tous les commerciaux), valide les remises
 *       importantes, bloque/debloque et fixe les plafonds de credit.</li>
 *   <li>{@link #MAGASINIER} : gere le stock et fait avancer les commandes
 *       validees (preparation, livraison).</li>
 *   <li>{@link #COMMERCIAL} : ne voit et ne gere que ses propres clients et
 *       leurs documents.</li>
 *   <li>{@link #RESPONSABLE_IMPORT} : gere le catalogue (produits, marques) et
 *       les fournisseurs, sans acces au commercial.</li>
 *   <li>{@link #COMPTABLE} : emet, encaisse et envoie les factures ; le reste
 *       (catalogue, devis, commandes, clients) lui est ouvert en lecture, le
 *       temps de facturer.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    RESPONSABLE_COMMERCIAL,
    MAGASINIER,
    COMMERCIAL,
    RESPONSABLE_IMPORT,
    COMPTABLE
}

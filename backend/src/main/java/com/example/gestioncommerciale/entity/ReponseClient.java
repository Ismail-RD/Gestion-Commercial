package com.example.gestioncommerciale.entity;

/**
 * Reponse du client au devis recu par email (via son lien personnel).
 * Attention : c'est une simple trace de ce que le client a repondu, elle ne
 * change PAS le statut du devis. La validation reste une action manuelle
 * effectuee dans l'application apres verification du bon de commande.
 */
public enum ReponseClient {
    ACCEPTE,
    REFUSE
}

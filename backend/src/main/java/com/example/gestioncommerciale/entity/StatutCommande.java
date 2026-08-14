package com.example.gestioncommerciale.entity;

public enum StatutCommande {
    // Remise de ligne superieure au seuil : rien ne bouge tant que
    // l'encadrement commercial n'a pas tranche.
    EN_ATTENTE_VALIDATION,
    EN_ATTENTE,
    VALIDEE,
    EN_PREPARATION,
    LIVREE,
    ANNULEE
}

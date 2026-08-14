package com.example.gestioncommerciale.entity;

public enum StatutDevis {
    BROUILLON,
    // Remise superieure au seuil autorise : en attente de validation par un admin
    EN_ATTENTE_VALIDATION,
    ENVOYE,
    ACCEPTE,
    REFUSE,
    EXPIRE
}

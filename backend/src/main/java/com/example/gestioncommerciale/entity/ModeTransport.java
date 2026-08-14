package com.example.gestioncommerciale.entity;

/**
 * Acheminement de la marchandise. Il conditionne les delais et la nature des
 * frais : un fret maritime se compte en semaines et en conteneurs, un fret
 * aerien en jours et au kilo.
 */
public enum ModeTransport {
    MARITIME,
    AERIEN,
    ROUTIER
}

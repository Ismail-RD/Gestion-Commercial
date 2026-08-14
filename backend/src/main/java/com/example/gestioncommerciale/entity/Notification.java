package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Un evenement porte a la connaissance d'une personne precise.
 *
 * <p>Une notification n'est pas un etat mais un fait : le tableau de bord dit
 * deja ce qui attend, elle dit ce qui vient de changer. D'ou l'adressage
 * nominatif -- une notification appartient a quelqu'un, et c'est lui seul qui
 * la lit et la marque lue.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeNotification type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NiveauNotification niveau;

    @NotNull
    @Column(nullable = false, length = 120)
    private String titre;

    @Column(length = 255)
    private String message;

    // --- Document concerne, pour ouvrir la bonne fiche au clic ---

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", length = 30)
    private TypeDocument typeDocument;

    @Column(name = "document_id")
    private Long documentId;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /** Nulle tant que le destinataire ne l'a pas ouverte. */
    @Column(name = "date_lecture")
    private LocalDateTime dateLecture;

    /**
     * Cle d'idempotence des alertes recurrentes. Une facture echue depuis trois
     * semaines ne doit pas produire vingt-et-une notifications : la cle porte la
     * periode ("FACTURE_ECHUE:42:2026-08"), et l'unicite est garantie en base
     * plutot que par une verification en Java, qui laisserait passer les
     * doublons en cas d'execution concurrente.
     */
    @Column(length = 120)
    private String cle;

    public boolean estLue() {
        return dateLecture != null;
    }
}

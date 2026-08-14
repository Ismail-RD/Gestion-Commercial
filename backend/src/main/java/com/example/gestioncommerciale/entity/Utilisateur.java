package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nom;

    @NotBlank
    private String prenom;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Vide tant que l'invite n'a pas suivi son lien : l'administrateur cree le
     * compte, l'utilisateur choisit lui-meme son mot de passe.
     */
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** Un compte invite reste inactif : il ne peut pas se connecter. */
    private boolean actif = true;

    // --- Invitation ---

    /** Jeton du lien personnel envoye par email, efface des qu'il a servi. */
    @Column(unique = true)
    private String tokenInvitation;

    /** Passe cette date, le lien ne vaut plus rien et doit etre renvoye. */
    private LocalDateTime invitationExpireLe;

    @CreationTimestamp
    private LocalDateTime dateCreation;
}

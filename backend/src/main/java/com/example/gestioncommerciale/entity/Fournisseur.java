package com.example.gestioncommerciale.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fournisseurs")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type_fournisseur", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Fournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nom;

    @Email
    @Column(unique = true)
    private String email;

    // Un fournisseur peut avoir plusieurs numeros de telephone.
    @ElementCollection
    @CollectionTable(name = "fournisseur_telephones", joinColumns = @JoinColumn(name = "fournisseur_id"))
    @Column(name = "telephone")
    @Builder.Default
    private List<String> telephones = new ArrayList<>();

    // Un fournisseur peut avoir plusieurs RIB.
    @ElementCollection
    @CollectionTable(name = "fournisseur_ribs", joinColumns = @JoinColumn(name = "fournisseur_id"))
    @Builder.Default
    private List<Rib> ribs = new ArrayList<>();

    private String adresse;

    // Colonne discriminante : renseignee par Hibernate via @DiscriminatorValue.
    // Pas de @NotNull ici, sinon Bean Validation echoue au persist (le champ
    // est encore null a ce moment, c'est un miroir en lecture seule).
    @Enumerated(EnumType.STRING)
    @Column(name = "type_fournisseur", insertable = false, updatable = false)
    private TypeFournisseur typeFournisseur;

    @CreationTimestamp
    private LocalDateTime dateCreation;
}

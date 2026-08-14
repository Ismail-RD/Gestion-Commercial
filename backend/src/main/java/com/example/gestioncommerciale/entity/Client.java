package com.example.gestioncommerciale.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type_client", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nom;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    // Un client peut avoir plusieurs numeros de telephone.
    @ElementCollection
    @CollectionTable(name = "client_telephones", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "telephone")
    @Builder.Default
    private List<String> telephones = new ArrayList<>();

    // Un client peut avoir plusieurs RIB.
    @ElementCollection
    @CollectionTable(name = "client_ribs", joinColumns = @JoinColumn(name = "client_id"))
    @Builder.Default
    private List<Rib> ribs = new ArrayList<>();

    private String adresse;

    // Plafond de credit : encours maximal tolere. Il n'existe pas de credit
    // illimite : a defaut d'autorisation explicite le plafond vaut 0, donc la
    // moindre facture impayee bloque le client.
    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal plafondCredit = BigDecimal.ZERO;

    // Etat credit du client. Passe automatiquement a BLOQUE quand l'encours
    // (factures impayees) depasse le plafond ; deblocage manuel par un admin.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutClient statut = StatutClient.ACTIF;

    // Colonne discriminante : renseignee par Hibernate via @DiscriminatorValue.
    // Pas de @NotNull ici, sinon Bean Validation echoue au persist (le champ
    // est encore null a ce moment, c'est un miroir en lecture seule).
    @Enumerated(EnumType.STRING)
    @Column(name = "type_client", insertable = false, updatable = false)
    private TypeClient typeClient;

    @CreationTimestamp
    private LocalDateTime dateCreation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id")
    private Utilisateur commercial;

    /**
     * Un client bloque ne doit plus rien recevoir de commercial : ni devis, ni
     * commande. La question se pose a plusieurs endroits, elle se repond ici.
     */
    public boolean estBloque() {
        return statut == StatutClient.BLOQUE;
    }
}

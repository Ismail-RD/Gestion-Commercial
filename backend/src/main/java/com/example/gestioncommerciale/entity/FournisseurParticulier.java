package com.example.gestioncommerciale.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "fournisseurs_particulier")
@DiscriminatorValue("PARTICULIER")
@PrimaryKeyJoinColumn(name = "fournisseur_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FournisseurParticulier extends Fournisseur {

    private String prenom;

    // Carte d'identite nationale (optionnelle).
    private String cin;
}

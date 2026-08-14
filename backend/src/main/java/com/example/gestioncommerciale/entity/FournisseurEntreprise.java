package com.example.gestioncommerciale.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "fournisseurs_entreprise")
@DiscriminatorValue("ENTREPRISE")
@PrimaryKeyJoinColumn(name = "fournisseur_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FournisseurEntreprise extends Fournisseur {

    @NotBlank
    private String raisonSociale;

    private String ice;

    // Identifiant fiscal marocain : 8 chiffres (optionnel). Remplace l'ancienne TVA intra.
    private String identifiantFiscal;
}

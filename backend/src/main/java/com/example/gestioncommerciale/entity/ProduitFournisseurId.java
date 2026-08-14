package com.example.gestioncommerciale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProduitFournisseurId implements Serializable {

    @Column(name = "produit_id")
    private Long produitId;

    @Column(name = "fournisseur_id")
    private Long fournisseurId;
}

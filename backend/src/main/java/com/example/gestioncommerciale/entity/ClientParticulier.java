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

import java.time.LocalDate;

@Entity
@Table(name = "clients_particulier")
@DiscriminatorValue("PARTICULIER")
@PrimaryKeyJoinColumn(name = "client_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ClientParticulier extends Client {

    private String prenom;

    private LocalDate dateNaissance;

    // Carte d'identite nationale (optionnelle).
    private String cin;
}

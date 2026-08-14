package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.ProduitFournisseur;
import com.example.gestioncommerciale.entity.ProduitFournisseurId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProduitFournisseurRepository extends JpaRepository<ProduitFournisseur, ProduitFournisseurId> {
}

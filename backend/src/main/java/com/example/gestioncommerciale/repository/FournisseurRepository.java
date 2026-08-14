package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Long>,
        JpaSpecificationExecutor<Fournisseur> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}

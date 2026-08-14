package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Marque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MarqueRepository extends JpaRepository<Marque, Long>,
        JpaSpecificationExecutor<Marque> {

    boolean existsByNom(String nom);

    boolean existsByNomAndIdNot(String nom, Long id);
}

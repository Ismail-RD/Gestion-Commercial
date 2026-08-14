package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Depot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    Optional<Depot> findByCode(String code);

    boolean existsByCode(String code);
}

package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.LigneCommande;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    /** Depot retenu au prelevement d'une ligne : il ne peut plus disparaitre. */
    boolean existsByDepotId(Long depotId);
}

package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long>,
        JpaSpecificationExecutor<Client> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Client> findByCommercialId(Long commercialId);

    /** Un commercial encore titulaire d un portefeuille ne peut pas etre efface. */
    boolean existsByCommercialId(Long commercialId);

    // --- Tableau de bord ---

    /** Clients bloques : plafond depasse, ils ne peuvent plus commander. */
    @Query("select c from Client c where c.statut = "
            + "com.example.gestioncommerciale.entity.StatutClient.BLOQUE "
            + "and (:commercialId is null or c.commercial.id = :commercialId) "
            + "order by c.nom asc")
    List<Client> bloques(@Param("commercialId") Long commercialId);

    /** Sans titulaire : personne ne les suit, et un commercial ne les verrait pas. */
    @Query("select c from Client c where c.commercial is null order by c.nom asc")
    List<Client> sansCommercial();

    long countByCommercialId(Long commercialId);
}

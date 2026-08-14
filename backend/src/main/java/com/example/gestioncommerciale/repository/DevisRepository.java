package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.StatutDevis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DevisRepository extends JpaRepository<Devis, Long>,
        JpaSpecificationExecutor<Devis> {

    /** Plus haut numero attribue pour ce prefixe, base de la numerotation. */
    @Query("select max(d.numero) from Devis d where d.numero like concat(:prefix, '%')")
    String dernierNumero(@Param("prefix") String prefix);

    boolean existsByClientId(Long clientId);

    /** Documents rediges par cet utilisateur : ils gardent sa trace. */
    boolean existsByCommercialId(Long commercialId);

    /** Retrouve le devis a partir du jeton du lien personnel envoye au client. */
    Optional<Devis> findByTokenClient(String tokenClient);

    // --- Tableau de bord ---

    @Query("select d from Devis d where d.statut = :statut "
            + "and (:commercialId is null or d.client.commercial.id = :commercialId) "
            + "order by d.dateCreation asc")
    List<Devis> parStatut(@Param("statut") StatutDevis statut,
                          @Param("commercialId") Long commercialId);

    @Query("select count(d) from Devis d where d.statut in :statuts "
            + "and (:commercialId is null or d.client.commercial.id = :commercialId)")
    long compterParStatuts(@Param("statuts") Collection<StatutDevis> statuts,
                           @Param("commercialId") Long commercialId);

    /** Devis partis chez le client dont la validite touche a sa fin. */
    @Query("select d from Devis d where d.statut = "
            + "com.example.gestioncommerciale.entity.StatutDevis.ENVOYE "
            + "and d.dateValidite between :aujourdhui and :limite "
            + "and (:commercialId is null or d.client.commercial.id = :commercialId) "
            + "order by d.dateValidite asc")
    List<Devis> expirantAvant(@Param("aujourdhui") LocalDate aujourdhui,
                              @Param("limite") LocalDate limite,
                              @Param("commercialId") Long commercialId);

    /** Envoyes au client, sans reponse de sa part : ils appellent une relance. */
    @Query("select d from Devis d where d.statut = "
            + "com.example.gestioncommerciale.entity.StatutDevis.ENVOYE "
            + "and d.dateEnvoiEmail is not null and d.reponseClient is null "
            + "and (:commercialId is null or d.client.commercial.id = :commercialId) "
            + "order by d.dateEnvoiEmail asc")
    List<Devis> envoyesSansReponse(@Param("commercialId") Long commercialId);

    /** Nombre de devis par statut, pour calculer un taux d'acceptation. */
    @Query("select d.statut, count(d) from Devis d "
            + "where (:commercialId is null or d.client.commercial.id = :commercialId) "
            + "group by d.statut")
    List<Object[]> comptesParStatut(@Param("commercialId") Long commercialId);

    /** Sorts des devis par commercial : de quoi comparer les taux d'acceptation. */
    @Query("select d.commercial.id, d.statut, count(d) from Devis d "
            + "where d.commercial is not null group by d.commercial.id, d.statut")
    List<Object[]> comptesParCommercialEtStatut();
}

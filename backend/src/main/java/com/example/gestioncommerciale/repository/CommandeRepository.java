package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.StatutCommande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommandeRepository extends JpaRepository<Commande, Long>,
        JpaSpecificationExecutor<Commande> {

    /** Plus haut numero attribue pour ce prefixe, base de la numerotation. */
    @Query("select max(c.numero) from Commande c where c.numero like concat(:prefix, '%')")
    String dernierNumero(@Param("prefix") String prefix);

    boolean existsByDevisId(Long devisId);

    boolean existsByClientId(Long clientId);

    /** Documents rediges par cet utilisateur : ils gardent sa trace. */
    boolean existsByCommercialId(Long commercialId);

    // --- Tableau de bord ---

    /** File de travail de l'entrepot : les commandes d'un statut donne. */
    @Query("select c from Commande c where c.statut = :statut "
            + "and (:commercialId is null or c.client.commercial.id = :commercialId) "
            + "order by c.dateCommande asc")
    List<Commande> parStatut(@Param("statut") StatutCommande statut,
                             @Param("commercialId") Long commercialId);

    @Query("select count(c) from Commande c where c.statut in :statuts "
            + "and (:commercialId is null or c.client.commercial.id = :commercialId)")
    long compterParStatuts(@Param("statuts") Collection<StatutCommande> statuts,
                           @Param("commercialId") Long commercialId);

    /**
     * Marchandise partie sans facture : de l'argent livre qui n'a pas encore ete
     * demande au client.
     */
    @Query("select c from Commande c where c.statut = "
            + "com.example.gestioncommerciale.entity.StatutCommande.LIVREE "
            + "and not exists (select 1 from Facture f where f.commande = c) "
            + "order by c.dateCommande asc")
    List<Commande> livreesNonFacturees();
}

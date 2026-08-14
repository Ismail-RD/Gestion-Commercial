package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.MouvementStock;
import com.example.gestioncommerciale.entity.TypeMouvement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long>,
        JpaSpecificationExecutor<MouvementStock> {

    /** Mouvements passes par cet utilisateur : ils gardent sa trace. */
    boolean existsByUtilisateurId(Long utilisateurId);

    /** Mouvements passes par ce depot : ils gardent sa trace. */
    boolean existsByDepotId(Long depotId);

    /** Volumes par type sur la fenetre : ce qui est entre, sorti, corrige. */
    @Query("""
            select new com.example.gestioncommerciale.repository.MouvementStockRepository$VolumeParType(
                m.type, sum(m.quantite), count(m))
            from MouvementStock m
            where m.dateMouvement >= :depuis
            group by m.type
            """)
    List<VolumeParType> volumesDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Ce qui tourne : quantites sorties par produit sur la fenetre.
     *
     * <p>Un mouvement enregistre la variation appliquee au stock : une sortie est
     * donc negative. On en prend la valeur absolue, sans quoi le classement
     * remonterait les produits qui bougent le moins.
     */
    @Query("""
            select new com.example.gestioncommerciale.repository.MouvementStockRepository$SortieProduit(
                p.id, p.reference, p.designation, abs(sum(m.quantite)))
            from MouvementStock m
            join m.produit p
            where m.type = com.example.gestioncommerciale.entity.TypeMouvement.SORTIE
              and m.dateMouvement >= :depuis
            group by p.id, p.reference, p.designation
            order by abs(sum(m.quantite)) desc
            """)
    List<SortieProduit> sortiesParProduitDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Date de la derniere sortie de chaque produit, toutes periodes confondues.
     * Sert a dater le sommeil : "immobilise depuis 120 jours" est plus parlant
     * que "aucune sortie sur la fenetre".
     */
    @Query("""
            select m.produit.id, max(m.dateMouvement)
            from MouvementStock m
            where m.type = com.example.gestioncommerciale.entity.TypeMouvement.SORTIE
            group by m.produit.id
            """)
    List<Object[]> derniereSortieParProduit();

    /** Corrections d'inventaire recentes, les plus fraiches d'abord. */
    @Query("""
            select m from MouvementStock m
            where m.type = com.example.gestioncommerciale.entity.TypeMouvement.AJUSTEMENT
              and m.dateMouvement >= :depuis
            order by m.dateMouvement desc
            """)
    List<MouvementStock> ajustementsDepuis(@Param("depuis") LocalDateTime depuis);

    /** Activite du jour : ce qui a bouge depuis ce matin. */
    long countByDateMouvementAfter(LocalDateTime depuis);

    record VolumeParType(TypeMouvement type, BigDecimal quantite, long nombre) {
    }

    record SortieProduit(Long produitId, String reference, String designation,
                         BigDecimal quantiteSortie) {
    }
}

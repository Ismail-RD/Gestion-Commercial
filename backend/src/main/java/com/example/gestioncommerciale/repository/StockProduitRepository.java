package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.StockProduit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockProduitRepository extends JpaRepository<StockProduit, Long>,
        JpaSpecificationExecutor<StockProduit> {

    Optional<StockProduit> findByProduitIdAndDepotId(Long produitId, Long depotId);

    /** Un depot encore rattache a des lignes de stock ne peut pas etre efface. */
    boolean existsByDepotId(Long depotId);

    // Recupere en une seule requete le stock de plusieurs produits (evite le N+1)
    List<StockProduit> findByProduitIdIn(Collection<Long> produitIds);

    // Stock physique total d'un produit, tous depots confondus.
    @Query("select coalesce(sum(sp.quantite), 0) from StockProduit sp where sp.produit.id = :produitId")
    BigDecimal quantiteTotale(@Param("produitId") Long produitId);

    /**
     * Photo du stock pour le tableau de bord : une ligne par couple
     * produit-depot, reduite aux seuls champs utiles. Une projection plutot que
     * les entites completes, parce que tout est ensuite agrege en memoire :
     * charger les produits entiers ne servirait a rien.
     */
    @Query("""
            select new com.example.gestioncommerciale.repository.StockProduitRepository$Photo(
                p.id, p.reference, p.designation, c.nom, d.code,
                sp.quantite, sp.quantiteReservee, p.prixUnitaireHT, p.coutRevientMoyen)
            from StockProduit sp
            join sp.produit p
            join sp.depot d
            left join p.categorie c
            """)
    List<Photo> photoDuStock();

    /** Une ligne de stock aplatie : produit, depot, quantites, prix. */
    record Photo(
            Long produitId,
            String reference,
            String designation,
            String categorie,
            String depotCode,
            BigDecimal quantite,
            BigDecimal quantiteReservee,
            BigDecimal prixUnitaireHT,
            /** Cout de revient moyen, null tant qu aucune reception ne l a etabli. */
            BigDecimal coutRevientMoyen
    ) {
    }
}

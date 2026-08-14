package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProduitRepository extends JpaRepository<Produit, Long>,
        JpaSpecificationExecutor<Produit> {

    boolean existsByReference(String reference);

    boolean existsByReferenceAndIdNot(String reference, Long id);

    // --- Tableau de bord ---

    /** Fiches incompletes : sans document technique a remettre au client. */
    @Query("select p from Produit p where p.ficheTechnique is null or p.ficheTechnique = '' "
            + "order by p.reference asc")
    List<Produit> sansFicheTechnique();

    /** Sans prix : invendables en l'etat, aucun montant ne peut etre calcule. */
    @Query("select p from Produit p where p.prixUnitaireHT is null or p.prixUnitaireHT = 0 "
            + "order by p.reference asc")
    List<Produit> sansPrix();

    /** Sans fournisseur rattache : impossible de savoir chez qui les commander. */
    @Query("select p from Produit p where p.fournisseurs is empty order by p.reference asc")
    List<Produit> sansFournisseur();

    /** Jamais recus en stock : le catalogue les annonce, l'entrepot ne les connait pas. */
    @Query("select p from Produit p where not exists "
            + "(select 1 from StockProduit sp where sp.produit = p) order by p.reference asc")
    List<Produit> jamaisEntresEnStock();
}

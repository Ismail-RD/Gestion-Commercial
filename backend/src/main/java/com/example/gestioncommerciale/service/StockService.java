package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.AjustementRequest;
import com.example.gestioncommerciale.dto.MouvementRequest;
import com.example.gestioncommerciale.dto.MouvementStockResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.StockApercuResponse;
import com.example.gestioncommerciale.dto.StockProduitResponse;
import com.example.gestioncommerciale.dto.TransfertRequest;
import com.example.gestioncommerciale.dto.filter.MouvementFilter;
import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import com.example.gestioncommerciale.dto.filter.StockFilter;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockService {

    StockProduitResponse entree(MouvementRequest request);

    StockProduitResponse sortie(MouvementRequest request);

    StockProduitResponse ajuster(AjustementRequest request);

    List<StockProduitResponse> transferer(TransfertRequest request);

    /**
     * Reserve une quantite d'un produit dans un depot au profit d'une commande
     * validee : le stock physique ne bouge pas, mais il n'est plus disponible
     * pour une autre commande. Leve un 409 si le disponible du depot
     * (quantite - deja reserve) est insuffisant.
     */
    void reserver(Long produitId, String depotCode, java.math.BigDecimal quantite);

    /** Libere une reservation (livraison effectuee, annulation, retrait de ligne). */
    void libererReservation(Long produitId, String depotCode, java.math.BigDecimal quantite);

    PageResponse<StockProduitResponse> listerStock(StockFilter filtre, Pageable pageable);

    /**
     * Vue par produit : chaque produit du catalogue avec sa quantite dans
     * chaque depot (0 inclus) et son total. Donne la visibilite complete,
     * contrairement a listerStock qui n'affiche que les lignes existantes.
     */
    PageResponse<StockApercuResponse> apercuParProduit(ProduitFilter filtre, Pageable pageable);

    PageResponse<MouvementStockResponse> listerMouvements(MouvementFilter filtre, Pageable pageable);
}

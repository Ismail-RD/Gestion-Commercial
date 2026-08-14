package com.example.gestioncommerciale.controller;

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
import com.example.gestioncommerciale.service.StockService;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    // Niveaux de stock courants (lignes produit x depot existantes)
    @GetMapping
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public PageResponse<StockProduitResponse> listerStock(StockFilter filtre, Pageable pageable) {
        return stockService.listerStock(filtre, pageable);
    }

    // Vue par produit : tout le catalogue avec le stock de chaque depot (0 inclus)
    @GetMapping("/apercu")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public PageResponse<StockApercuResponse> apercuParProduit(ProduitFilter filtre, Pageable pageable) {
        return stockService.apercuParProduit(filtre, pageable);
    }

    // Historique des mouvements
    @GetMapping("/mouvements")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public PageResponse<MouvementStockResponse> listerMouvements(MouvementFilter filtre, Pageable pageable) {
        return stockService.listerMouvements(filtre, pageable);
    }

    @PostMapping("/entree")
    @PreAuthorize(Autorisations.ECRIRE_STOCK)
    public StockProduitResponse entree(@Valid @RequestBody MouvementRequest request) {
        return stockService.entree(request);
    }

    @PostMapping("/sortie")
    @PreAuthorize(Autorisations.ECRIRE_STOCK)
    public StockProduitResponse sortie(@Valid @RequestBody MouvementRequest request) {
        return stockService.sortie(request);
    }

    @PostMapping("/ajustement")
    @PreAuthorize(Autorisations.ECRIRE_STOCK)
    public StockProduitResponse ajuster(@Valid @RequestBody AjustementRequest request) {
        return stockService.ajuster(request);
    }

    @PostMapping("/transfert")
    @PreAuthorize(Autorisations.ECRIRE_STOCK)
    public List<StockProduitResponse> transferer(@Valid @RequestBody TransfertRequest request) {
        return stockService.transferer(request);
    }
}

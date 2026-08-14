package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.AjustementRequest;
import com.example.gestioncommerciale.dto.MouvementRequest;
import com.example.gestioncommerciale.dto.MouvementStockResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.StockApercuResponse;
import com.example.gestioncommerciale.dto.StockDepotResponse;
import com.example.gestioncommerciale.dto.StockProduitResponse;
import com.example.gestioncommerciale.dto.TransfertRequest;
import com.example.gestioncommerciale.dto.filter.MouvementFilter;
import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import com.example.gestioncommerciale.dto.filter.StockFilter;
import com.example.gestioncommerciale.entity.Depot;
import com.example.gestioncommerciale.entity.MouvementStock;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.StockProduit;
import com.example.gestioncommerciale.entity.TypeMouvement;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.repository.StockProduitRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.example.gestioncommerciale.service.StockService;
import com.example.gestioncommerciale.specification.MouvementStockSpecifications;
import com.example.gestioncommerciale.specification.ProduitSpecifications;
import com.example.gestioncommerciale.specification.StockProduitSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class StockServiceImpl implements StockService {

    private final StockProduitRepository stockProduitRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final ProduitRepository produitRepository;
    private final DepotRepository depotRepository;
    private final CurrentUserService currentUserService;

    public StockServiceImpl(StockProduitRepository stockProduitRepository,
                            MouvementStockRepository mouvementStockRepository,
                            ProduitRepository produitRepository,
                            DepotRepository depotRepository,
                            CurrentUserService currentUserService) {
        this.stockProduitRepository = stockProduitRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.produitRepository = produitRepository;
        this.depotRepository = depotRepository;
        this.currentUserService = currentUserService;
    }

    @Override
    public StockProduitResponse entree(MouvementRequest request) {
        StockProduit stock = appliquer(request.produitId(), request.depotCode(),
                request.quantite(), TypeMouvement.ENTREE, request.motif());
        return toStockResponse(stock);
    }

    @Override
    public StockProduitResponse sortie(MouvementRequest request) {
        StockProduit stock = appliquer(request.produitId(), request.depotCode(),
                request.quantite().negate(), TypeMouvement.SORTIE, request.motif());
        return toStockResponse(stock);
    }

    @Override
    public StockProduitResponse ajuster(AjustementRequest request) {
        Produit produit = getProduit(request.produitId());
        Depot depot = getDepot(request.depotCode());
        StockProduit stock = getOuCreerStock(produit, depot);

        BigDecimal delta = request.nouvelleQuantite().subtract(stock.getQuantite());
        stock.setQuantite(request.nouvelleQuantite());
        stockProduitRepository.save(stock);
        enregistrerMouvement(produit, depot, TypeMouvement.AJUSTEMENT, delta,
                stock.getQuantite(), request.motif());
        return toStockResponse(stock);
    }

    @Override
    public List<StockProduitResponse> transferer(TransfertRequest request) {
        if (request.depotSource().equals(request.depotDestination())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les depots source et destination doivent etre differents");
        }
        String motif = motifTransfert(request);
        StockProduit source = appliquer(request.produitId(), request.depotSource(),
                request.quantite().negate(), TypeMouvement.SORTIE, motif);
        StockProduit destination = appliquer(request.produitId(), request.depotDestination(),
                request.quantite(), TypeMouvement.ENTREE, motif);
        return List.of(toStockResponse(source), toStockResponse(destination));
    }

    @Override
    public void reserver(Long produitId, String depotCode, BigDecimal quantite) {
        Produit produit = getProduit(produitId);
        Depot depot = getDepot(depotCode);
        StockProduit stock = getOuCreerStock(produit, depot);

        BigDecimal disponible = stock.getQuantite().subtract(reservee(stock));
        if (quantite.compareTo(disponible) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuffisant dans le depot " + depotCode + " pour reserver "
                            + quantite + " x " + produit.getReference()
                            + " (disponible : " + disponible + ")");
        }
        stock.setQuantiteReservee(reservee(stock).add(quantite));
        stockProduitRepository.save(stock);
    }

    @Override
    public void libererReservation(Long produitId, String depotCode, BigDecimal quantite) {
        Depot depot = getDepot(depotCode);
        stockProduitRepository.findByProduitIdAndDepotId(produitId, depot.getId())
                .ifPresent(stock -> {
                    // Garde-fou : la reservation ne peut pas devenir negative.
                    stock.setQuantiteReservee(reservee(stock).subtract(quantite).max(BigDecimal.ZERO));
                    stockProduitRepository.save(stock);
                });
    }

    private BigDecimal reservee(StockProduit stock) {
        return stock.getQuantiteReservee() != null ? stock.getQuantiteReservee() : BigDecimal.ZERO;
    }

    private BigDecimal somme(List<StockDepotResponse> lignes,
                             java.util.function.Function<StockDepotResponse, BigDecimal> champ) {
        return lignes.stream().map(champ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockProduitResponse> listerStock(StockFilter filtre, Pageable pageable) {
        return PageResponse.from(
                stockProduitRepository.findAll(StockProduitSpecifications.avecFiltre(filtre), pageable)
                        .map(this::toStockResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockApercuResponse> apercuParProduit(ProduitFilter filtre, Pageable pageable) {
        // 1. Page de produits (filtrable comme le catalogue)
        Page<Produit> produits = produitRepository.findAll(
                ProduitSpecifications.avecFiltre(filtre), pageable);

        List<Long> produitIds = produits.getContent().stream().map(Produit::getId).toList();

        // 2. Tout le stock de ces produits en une seule requete
        Map<Long, Map<String, StockProduit>> stockParProduit = produitIds.isEmpty()
                ? Map.of()
                : stockProduitRepository.findByProduitIdIn(produitIds).stream()
                        .collect(Collectors.groupingBy(
                                sp -> sp.getProduit().getId(),
                                Collectors.toMap(sp -> sp.getDepot().getCode(), sp -> sp)));

        // 3. Tous les depots, pour afficher aussi ceux sans stock (a 0)
        List<Depot> depots = depotRepository.findAll(Sort.by("code"));

        return PageResponse.from(produits.map(p -> {
            Map<String, StockProduit> parDepot = stockParProduit.getOrDefault(p.getId(), Map.of());

            List<StockDepotResponse> lignes = depots.stream()
                    .map(d -> {
                        StockProduit sp = parDepot.get(d.getCode());
                        BigDecimal quantite = sp != null ? sp.getQuantite() : BigDecimal.ZERO;
                        BigDecimal reservee = sp != null ? reservee(sp) : BigDecimal.ZERO;
                        return new StockDepotResponse(d.getCode(), quantite, reservee,
                                quantite.subtract(reservee));
                    })
                    .toList();

            BigDecimal total = somme(lignes, StockDepotResponse::quantite);
            BigDecimal reservee = somme(lignes, StockDepotResponse::quantiteReservee);

            return new StockApercuResponse(
                    p.getId(),
                    p.getReference(),
                    p.getDesignation(),
                    p.getCategorie() != null ? p.getCategorie().getNom() : null,
                    lignes,
                    total,
                    reservee,
                    total.subtract(reservee));
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MouvementStockResponse> listerMouvements(MouvementFilter filtre, Pageable pageable) {
        return PageResponse.from(
                mouvementStockRepository.findAll(MouvementStockSpecifications.avecFiltre(filtre), pageable)
                        .map(this::toMouvementResponse));
    }

    // --- Coeur : application d'un mouvement signe ---

    private StockProduit appliquer(Long produitId, String depotCode, BigDecimal quantiteSignee,
                                   TypeMouvement type, String motif) {
        Produit produit = getProduit(produitId);
        Depot depot = getDepot(depotCode);
        StockProduit stock = getOuCreerStock(produit, depot);

        BigDecimal nouveau = stock.getQuantite().add(quantiteSignee);
        // Une sortie ne peut pas entamer ce qui est promis a une commande validee :
        // le plancher est la quantite reservee, pas zero.
        BigDecimal plancher = quantiteSignee.signum() < 0 && type != TypeMouvement.AJUSTEMENT
                ? reservee(stock) : BigDecimal.ZERO;
        if (nouveau.compareTo(plancher) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuffisant dans le depot " + depotCode + " (disponible : "
                            + stock.getQuantite().subtract(plancher) + ")");
        }
        stock.setQuantite(nouveau);
        stockProduitRepository.save(stock);
        enregistrerMouvement(produit, depot, type, quantiteSignee, nouveau, motif);
        return stock;
    }

    private void enregistrerMouvement(Produit produit, Depot depot, TypeMouvement type,
                                      BigDecimal quantiteSignee, BigDecimal quantiteApres, String motif) {
        Utilisateur auteur = currentUserService.getUtilisateurCourant();
        mouvementStockRepository.save(MouvementStock.builder()
                .produit(produit)
                .depot(depot)
                .type(type)
                .quantite(quantiteSignee)
                .quantiteApres(quantiteApres)
                .motif(motif)
                .utilisateur(auteur)
                .build());
    }

    private StockProduit getOuCreerStock(Produit produit, Depot depot) {
        return stockProduitRepository.findByProduitIdAndDepotId(produit.getId(), depot.getId())
                .orElseGet(() -> StockProduit.builder()
                        .produit(produit)
                        .depot(depot)
                        .quantite(BigDecimal.ZERO)
                        .build());
    }

    private Produit getProduit(Long id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
    }

    private Depot getDepot(String code) {
        return depotRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Depot", code));
    }

    private String motifTransfert(TransfertRequest request) {
        String base = "Transfert " + request.depotSource() + " -> " + request.depotDestination();
        return request.motif() != null ? base + " (" + request.motif() + ")" : base;
    }

    private StockProduitResponse toStockResponse(StockProduit s) {
        Produit p = s.getProduit();
        Depot d = s.getDepot();
        return new StockProduitResponse(
                s.getId(),
                p.getId(),
                p.getReference(),
                p.getDesignation(),
                d.getCode(),
                s.getQuantite()
        );
    }

    private MouvementStockResponse toMouvementResponse(MouvementStock m) {
        Utilisateur u = m.getUtilisateur();
        return new MouvementStockResponse(
                m.getId(),
                m.getProduit().getId(),
                m.getProduit().getDesignation(),
                m.getDepot().getCode(),
                m.getType(),
                m.getQuantite(),
                m.getQuantiteApres(),
                m.getMotif(),
                m.getDateMouvement(),
                u != null ? u.getPrenom() + " " + u.getNom() : null
        );
    }
}

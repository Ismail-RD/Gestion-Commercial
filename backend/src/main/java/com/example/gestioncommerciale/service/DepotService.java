package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.DepotRequest;
import com.example.gestioncommerciale.entity.Depot;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CommandeFournisseurRepository;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.repository.LigneCommandeRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository;
import com.example.gestioncommerciale.repository.StockProduitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Gestion des depots par l'administrateur.
 *
 * <p>Le code identifie le depot dans toute l'application : il est normalise en
 * majuscules pour qu'un "sh" saisi a la va-vite ne cree pas un second depot a
 * cote de "SH". Le renommage reste sans danger, les rattachements se font par
 * identifiant et non par code.
 *
 * <p>Un depot qui a servi ne s'efface pas : ses lignes de stock, ses mouvements
 * et les prelevements de commandes le referencent, et cet historique doit
 * rester lisible.
 */
@Service
public class DepotService {

    private final DepotRepository depotRepository;
    private final StockProduitRepository stockProduitRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;

    public DepotService(DepotRepository depotRepository,
                        StockProduitRepository stockProduitRepository,
                        MouvementStockRepository mouvementStockRepository,
                        LigneCommandeRepository ligneCommandeRepository,
                        CommandeFournisseurRepository commandeFournisseurRepository) {
        this.depotRepository = depotRepository;
        this.stockProduitRepository = stockProduitRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.commandeFournisseurRepository = commandeFournisseurRepository;
    }

    @Transactional(readOnly = true)
    public Depot trouverParId(Long id) {
        return getOrThrow(id);
    }

    @Transactional
    public Depot creer(DepotRequest request) {
        String code = normaliser(request.code());
        if (depotRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un depot porte deja le code " + code);
        }
        return depotRepository.save(Depot.builder().code(code).build());
    }

    @Transactional
    public Depot modifier(Long id, DepotRequest request) {
        Depot depot = getOrThrow(id);
        String code = normaliser(request.code());
        depotRepository.findByCode(code)
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un autre depot porte deja le code " + code);
                });
        depot.setCode(code);
        return depotRepository.save(depot);
    }

    @Transactional
    public void supprimer(Long id) {
        Depot depot = getOrThrow(id);
        if (stockProduitRepository.existsByDepotId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le depot " + depot.getCode() + " porte encore des lignes de stock : "
                            + "transferez son contenu vers un autre depot d'abord");
        }
        if (commandeFournisseurRepository.existsByDepotReceptionId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le depot " + depot.getCode() + " est le lieu de reception de commandes "
                            + "fournisseur : rattachez-les ailleurs d'abord");
        }
        if (mouvementStockRepository.existsByDepotId(id)
                || ligneCommandeRepository.existsByDepotId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le depot " + depot.getCode() + " figure dans des mouvements de stock "
                            + "ou des commandes : cet historique doit rester lisible");
        }
        depotRepository.delete(depot);
    }

    /** Code en majuscules, sans espaces autour : c'est une reference, pas un libelle. */
    private String normaliser(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Depot getOrThrow(Long id) {
        return depotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Depot", id));
    }
}

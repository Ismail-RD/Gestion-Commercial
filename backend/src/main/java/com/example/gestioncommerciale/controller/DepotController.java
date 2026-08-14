package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.DepotRequest;
import com.example.gestioncommerciale.dto.DepotResponse;
import com.example.gestioncommerciale.entity.Depot;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.security.Autorisations;
import com.example.gestioncommerciale.service.DepotService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Depots. La lecture est ouverte : elle alimente les ecrans de stock et le
 * choix du depot de prelevement a la validation d'une commande. La structure du
 * reseau de depots, elle, est du ressort de l'administrateur.
 */
@RestController
@RequestMapping("/api/depots")
public class DepotController {

    private final DepotRepository depotRepository;
    private final DepotService depotService;

    public DepotController(DepotRepository depotRepository, DepotService depotService) {
        this.depotRepository = depotRepository;
        this.depotService = depotService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public List<DepotResponse> lister() {
        return depotRepository.findAll(Sort.by("code")).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public DepotResponse trouver(@PathVariable Long id) {
        return toResponse(depotService.trouverParId(id));
    }

    @PostMapping
    @PreAuthorize(Autorisations.ADMINISTRER)
    public ResponseEntity<DepotResponse> creer(@Valid @RequestBody DepotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(depotService.creer(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public DepotResponse modifier(@PathVariable Long id,
                                  @Valid @RequestBody DepotRequest request) {
        return toResponse(depotService.modifier(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        depotService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    private DepotResponse toResponse(Depot d) {
        return new DepotResponse(d.getId(), d.getCode());
    }
}

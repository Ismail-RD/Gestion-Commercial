package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.MarqueRequest;
import com.example.gestioncommerciale.dto.MarqueResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.service.MarqueService;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marques")
public class MarqueController {

    private final MarqueService marqueService;

    public MarqueController(MarqueService marqueService) {
        this.marqueService = marqueService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public PageResponse<MarqueResponse> lister(@RequestParam(required = false) String nom, Pageable pageable) {
        return marqueService.lister(nom, pageable);
    }

    @GetMapping("/all")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public List<MarqueResponse> listerToutes() {
        return marqueService.listerToutes();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public MarqueResponse trouver(@PathVariable Long id) {
        return marqueService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ResponseEntity<MarqueResponse> creer(@Valid @RequestBody MarqueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(marqueService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public MarqueResponse modifier(@PathVariable Long id, @Valid @RequestBody MarqueRequest request) {
        return marqueService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        marqueService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.FournisseurRequest;
import com.example.gestioncommerciale.dto.FournisseurResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.FournisseurFilter;
import com.example.gestioncommerciale.service.FournisseurService;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fournisseurs")
public class FournisseurController {

    private final FournisseurService fournisseurService;

    public FournisseurController(FournisseurService fournisseurService) {
        this.fournisseurService = fournisseurService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public PageResponse<FournisseurResponse> lister(FournisseurFilter filtre, Pageable pageable) {
        return fournisseurService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public FournisseurResponse trouver(@PathVariable Long id) {
        return fournisseurService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public ResponseEntity<FournisseurResponse> creer(@Valid @RequestBody FournisseurRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fournisseurService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public FournisseurResponse modifier(@PathVariable Long id, @Valid @RequestBody FournisseurRequest request) {
        return fournisseurService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        fournisseurService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

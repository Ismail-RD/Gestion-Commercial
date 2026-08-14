package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.CategorieRequest;
import com.example.gestioncommerciale.dto.CategorieResponse;
import com.example.gestioncommerciale.service.CategorieService;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategorieController {

    private final CategorieService categorieService;

    public CategorieController(CategorieService categorieService) {
        this.categorieService = categorieService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public List<CategorieResponse> lister() {
        return categorieService.lister();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public CategorieResponse trouver(@PathVariable Long id) {
        return categorieService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_CATEGORIE)
    public ResponseEntity<CategorieResponse> creer(@Valid @RequestBody CategorieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATEGORIE)
    public CategorieResponse modifier(@PathVariable Long id, @Valid @RequestBody CategorieRequest request) {
        return categorieService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATEGORIE)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        categorieService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ProduitRequest;
import com.example.gestioncommerciale.dto.ProduitResponse;
import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import com.example.gestioncommerciale.service.ProduitService;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/produits")
public class ProduitController {

    private final ProduitService produitService;

    public ProduitController(ProduitService produitService) {
        this.produitService = produitService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public PageResponse<ProduitResponse> lister(ProduitFilter filtre, Pageable pageable) {
        return produitService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public ProduitResponse trouver(@PathVariable Long id) {
        return produitService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ResponseEntity<ProduitResponse> creer(@Valid @RequestBody ProduitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produitService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ProduitResponse modifier(@PathVariable Long id, @Valid @RequestBody ProduitRequest request) {
        return produitService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        produitService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // --- Fiche technique ---

    @PostMapping(value = "/{id}/fiche-technique", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ProduitResponse ajouterFicheTechnique(@PathVariable Long id,
                                                 @RequestParam("fichier") MultipartFile fichier) {
        return produitService.ajouterFicheTechnique(id, fichier);
    }

    @GetMapping("/{id}/fiche-technique")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public ResponseEntity<Resource> telechargerFicheTechnique(@PathVariable Long id) {
        Resource fichier = produitService.telechargerFicheTechnique(id);
        MediaType type = MediaTypeFactory.getMediaType(fichier)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fichier.getFilename()).build().toString())
                .body(fichier);
    }

    @DeleteMapping("/{id}/fiche-technique")
    @PreAuthorize(Autorisations.ECRIRE_CATALOGUE)
    public ProduitResponse supprimerFicheTechnique(@PathVariable Long id) {
        return produitService.supprimerFicheTechnique(id);
    }
}

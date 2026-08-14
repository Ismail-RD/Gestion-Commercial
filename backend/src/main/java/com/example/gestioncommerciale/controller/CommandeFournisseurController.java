package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.CommandeFournisseurRequest;
import com.example.gestioncommerciale.dto.CommandeFournisseurResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ReceptionCommandeFournisseurRequest;
import com.example.gestioncommerciale.entity.StatutCommandeFournisseur;
import com.example.gestioncommerciale.security.Autorisations;
import com.example.gestioncommerciale.service.CommandeFournisseurService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commandes fournisseur. Meme perimetre que les fournisseurs eux-memes :
 * l'achat est le metier du responsable import, pas de la force de vente.
 */
@RestController
@RequestMapping("/api/commandes-fournisseur")
public class CommandeFournisseurController {

    private final CommandeFournisseurService service;

    public CommandeFournisseurController(CommandeFournisseurService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public PageResponse<CommandeFournisseurResponse> lister(Pageable pageable) {
        return service.lister(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse trouver(@PathVariable Long id) {
        return service.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public ResponseEntity<CommandeFournisseurResponse> creer(
            @Valid @RequestBody CommandeFournisseurRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(request));
    }

    /** Tant que le bon de commande n'est pas emis. */
    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse modifier(
            @PathVariable Long id, @Valid @RequestBody CommandeFournisseurRequest request) {
        return service.modifier(id, request);
    }

    /** Emission du bon de commande : les lignes sont figees ensuite. */
    @PostMapping("/{id}/emettre")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse emettre(@PathVariable Long id) {
        return service.emettre(id);
    }

    /** Avancement du transit : EN_TRANSIT, EN_DOUANE. */
    @PatchMapping("/{id}/statut")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse changerStatut(
            @PathVariable Long id, @RequestParam StatutCommandeFournisseur statut) {
        return service.changerStatut(id, statut);
    }

    /** Entree en stock de ce qui est reellement arrive. */
    @PostMapping("/{id}/receptionner")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse receptionner(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReceptionCommandeFournisseurRequest request) {
        return service.receptionner(id, request);
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public CommandeFournisseurResponse annuler(@PathVariable Long id) {
        return service.annuler(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ACCES_FOURNISSEUR)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

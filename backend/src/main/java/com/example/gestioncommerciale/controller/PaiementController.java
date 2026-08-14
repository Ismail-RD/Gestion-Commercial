package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.PaiementRequest;
import com.example.gestioncommerciale.dto.PaiementResponse;
import com.example.gestioncommerciale.dto.RejetPaiementRequest;
import com.example.gestioncommerciale.security.Autorisations;
import com.example.gestioncommerciale.service.PaiementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Encaissements. Especes, carte et virement se soldent a l'enregistrement ;
 * cheques et traites passent par les trois etapes qui suivent, et ne comptent
 * dans le montant paye qu'une fois encaisses.
 */
@RestController
@RequestMapping("/api/paiements")
public class PaiementController {

    private final PaiementService paiementService;

    public PaiementController(PaiementService paiementService) {
        this.paiementService = paiementService;
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public ResponseEntity<PaiementResponse> enregistrer(@Valid @RequestBody PaiementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.enregistrer(request));
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public List<PaiementResponse> listerParFacture(@RequestParam Long factureId) {
        return paiementService.listerParFacture(factureId);
    }

    /** Effets en attente d'encaissement, echeances les plus proches d'abord. */
    @GetMapping("/effets")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public List<PaiementResponse> portefeuilleEffets() {
        return paiementService.portefeuilleEffets();
    }

    @PostMapping("/{id}/deposer")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public PaiementResponse deposer(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateRemise) {
        return paiementService.deposer(id, dateRemise);
    }

    @PostMapping("/{id}/encaisser")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public PaiementResponse encaisser(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEncaissement) {
        return paiementService.encaisser(id, dateEncaissement);
    }

    /** Un encaissement ne s'efface pas : il faut le rejeter d'abord. */
    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        paiementService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rejeter")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public PaiementResponse rejeter(@PathVariable Long id,
                                    @Valid @RequestBody RejetPaiementRequest request) {
        return paiementService.rejeter(id, request.motif());
    }
}

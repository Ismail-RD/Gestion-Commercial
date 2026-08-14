package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.FactureModificationRequest;
import com.example.gestioncommerciale.dto.FactureRequest;
import com.example.gestioncommerciale.dto.FactureResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.FactureFilter;
import com.example.gestioncommerciale.service.FactureEmailService;
import com.example.gestioncommerciale.service.FacturePdfService;
import com.example.gestioncommerciale.service.FactureService;
import jakarta.validation.Valid;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/factures")
public class FactureController {

    private final FactureService factureService;
    private final FacturePdfService facturePdfService;
    private final FactureEmailService factureEmailService;

    public FactureController(FactureService factureService,
                             FacturePdfService facturePdfService,
                             FactureEmailService factureEmailService) {
        this.factureService = factureService;
        this.facturePdfService = facturePdfService;
        this.factureEmailService = factureEmailService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public PageResponse<FactureResponse> lister(FactureFilter filtre, Pageable pageable) {
        return factureService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public FactureResponse trouver(@PathVariable Long id) {
        return factureService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public ResponseEntity<FactureResponse> creer(@Valid @RequestBody FactureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(factureService.creerDepuisCommande(request));
    }

    /** Genere le PDF de la facture (mise en page SOGETHERM). */
    @GetMapping("/{id}/pdf")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = facturePdfService.genererFacturePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("facture-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    /** Envoie la facture au client par email, PDF en piece jointe. */
    @PostMapping("/{id}/envoyer-email")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public ResponseEntity<Void> envoyerEmail(@PathVariable Long id) {
        factureEmailService.envoyerAuClient(id);
        return ResponseEntity.noContent().build();
    }

    /** Seule l'echeance se modifie : le reste decoule de la commande facturee. */
    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public FactureResponse modifier(@PathVariable Long id,
                                    @Valid @RequestBody FactureModificationRequest request) {
        return factureService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_FACTURE)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        factureService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

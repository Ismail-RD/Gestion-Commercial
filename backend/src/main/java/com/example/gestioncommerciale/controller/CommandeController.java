package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.ChangementStatutRequest;
import com.example.gestioncommerciale.dto.CommandeRequest;
import com.example.gestioncommerciale.dto.CommandeResponse;
import com.example.gestioncommerciale.dto.ModificationLignesRequest;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ValidationCommandeRequest;
import com.example.gestioncommerciale.dto.filter.CommandeFilter;
import com.example.gestioncommerciale.service.BonLivraisonPdfService;
import com.example.gestioncommerciale.service.CommandeService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commandes")
public class CommandeController {

    private final CommandeService commandeService;
    private final BonLivraisonPdfService bonLivraisonPdfService;

    public CommandeController(CommandeService commandeService,
                              BonLivraisonPdfService bonLivraisonPdfService) {
        this.commandeService = commandeService;
        this.bonLivraisonPdfService = bonLivraisonPdfService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public PageResponse<CommandeResponse> lister(CommandeFilter filtre, Pageable pageable) {
        return commandeService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public CommandeResponse trouver(@PathVariable Long id) {
        return commandeService.trouverParId(id);
    }

    /** Commande saisie directement, sans devis prealable. */
    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<CommandeResponse> creer(@Valid @RequestBody CommandeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandeService.creer(request));
    }

    /** Met a jour le client et les lignes d'une commande. */
    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public CommandeResponse modifier(@PathVariable Long id,
                                     @Valid @RequestBody CommandeRequest request) {
        return commandeService.modifier(id, request);
    }

    @PostMapping("/depuis-devis/{devisId}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<CommandeResponse> creerDepuisDevis(@PathVariable Long devisId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandeService.creerDepuisDevis(devisId));
    }

    // Validation avec prise de stock : chaque ligne indique son depot de prelevement
    @PostMapping("/{id}/valider")
    @PreAuthorize(Autorisations.TRAITER_COMMANDE)
    public CommandeResponse valider(@PathVariable Long id,
                                    @Valid @RequestBody ValidationCommandeRequest request) {
        return commandeService.valider(id, request);
    }

    /**
     * Redefinit les lignes avant l'edition du bon de livraison : retrait de
     * lignes, ajout de produits figurant sur le devis d'origine, ajustement des
     * quantites. Le stock (reservation ou sortie) suit automatiquement.
     */
    @PutMapping("/{id}/lignes")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public CommandeResponse modifierLignes(@PathVariable Long id,
                                           @Valid @RequestBody ModificationLignesRequest request) {
        return commandeService.modifierLignes(id, request);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public CommandeResponse changerStatut(@PathVariable Long id,
                                          @Valid @RequestBody ChangementStatutRequest request) {
        return commandeService.changerStatut(id, request.statut());
    }

    /** Aval sur une remise excessive : reserve a l'encadrement commercial. */
    @PostMapping("/{id}/valider-remise")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public CommandeResponse validerRemise(@PathVariable Long id) {
        return commandeService.validerRemise(id);
    }

    /**
     * Bon de livraison de la commande. {@code avecPrix=false} imprime le bon
     * sans les colonnes de prix ni le bloc des totaux.
     */
    @GetMapping("/{id}/bon-livraison")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ResponseEntity<byte[]> bonLivraison(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean avecPrix) {
        byte[] pdf = bonLivraisonPdfService.genererBonLivraisonPdf(id, avecPrix);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("bon-livraison-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    /**
     * Bon de preparation : liste de picking (sans prix ni signature) pour
     * preparer physiquement la commande. Utile quand elle est EN_PREPARATION.
     */
    @GetMapping("/{id}/preparation")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ResponseEntity<byte[]> bonPreparation(@PathVariable Long id) {
        byte[] pdf = bonLivraisonPdfService.genererBonPreparationPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("preparation-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        commandeService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.DevisRequest;
import com.example.gestioncommerciale.dto.DevisResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ReponseClientRequest;
import com.example.gestioncommerciale.dto.filter.DevisFilter;
import com.example.gestioncommerciale.service.DevisEmailService;
import com.example.gestioncommerciale.service.DevisPdfService;
import com.example.gestioncommerciale.service.DevisPublicService;
import com.example.gestioncommerciale.service.DevisService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/api/devis")
public class DevisController {

    private final DevisService devisService;
    private final DevisPdfService devisPdfService;
    private final DevisEmailService devisEmailService;
    private final DevisPublicService devisPublicService;

    public DevisController(DevisService devisService,
                           DevisPdfService devisPdfService,
                           DevisEmailService devisEmailService,
                           DevisPublicService devisPublicService) {
        this.devisService = devisService;
        this.devisPdfService = devisPdfService;
        this.devisEmailService = devisEmailService;
        this.devisPublicService = devisPublicService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public PageResponse<DevisResponse> lister(DevisFilter filtre, Pageable pageable) {
        return devisService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public DevisResponse trouver(@PathVariable Long id) {
        return devisService.trouverParId(id);
    }

    /** Genere le PDF du devis (mise en page SOGETHERM). */
    @GetMapping("/{id}/pdf")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = devisPdfService.genererDevisPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("devis-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<DevisResponse> creer(@Valid @RequestBody DevisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(devisService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public DevisResponse modifier(@PathVariable Long id, @Valid @RequestBody DevisRequest request) {
        return devisService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        devisService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // --- Workflow ---

    @PostMapping("/{id}/envoyer")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public DevisResponse envoyer(@PathVariable Long id) {
        return devisService.envoyer(id);
    }

    @PostMapping("/{id}/accepter")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public DevisResponse accepter(@PathVariable Long id,
                                  @RequestBody(required = false) ReponseClientRequest reponse) {
        return devisService.accepter(id, reponse);
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public DevisResponse refuser(@PathVariable Long id,
                                 @RequestBody(required = false) ReponseClientRequest reponse) {
        return devisService.refuser(id, reponse);
    }

    // --- Envoi au client par email ---

    /**
     * Envoie le devis au client (PDF joint + lien personnel pour accepter ou
     * refuser). Le statut du devis reste inchange.
     */
    @PostMapping("/{id}/envoyer-email")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<Void> envoyerEmail(@PathVariable Long id) {
        devisEmailService.envoyerAuClient(id);
        return ResponseEntity.noContent().build();
    }

    /** Telecharge le bon de commande depose par le client, pour verification. */
    @GetMapping("/{id}/bon-commande")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ResponseEntity<Resource> bonCommande(@PathVariable Long id) {
        Resource fichier = devisPublicService.bonCommande(id);
        String nom = devisPublicService.nomBonCommande(id);
        return ResponseEntity.ok()
                .contentType(typeMime(nom))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("bon-commande-" + id + extension(nom))
                                .build().toString())
                .body(fichier);
    }

    /** Type deduit de l'extension : les bons de commande sont des PDF ou des scans. */
    private static MediaType typeMime(String nom) {
        String ext = extension(nom).toLowerCase();
        return switch (ext) {
            case ".png" -> MediaType.IMAGE_PNG;
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_PDF;
        };
    }

    private static String extension(String nom) {
        if (nom == null) {
            return ".pdf";
        }
        int point = nom.lastIndexOf('.');
        return point >= 0 ? nom.substring(point) : ".pdf";
    }

    // --- Validation de remise (encadrement commercial) ---

    /** Valide un devis mis en attente pour remise excessive : passe a ENVOYE. */
    @PostMapping("/{id}/valider-remise")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public DevisResponse validerRemise(@PathVariable Long id) {
        return devisService.validerRemise(id);
    }

    /** Refuse la remise : le devis retourne a BROUILLON pour revision. */
    @PostMapping("/{id}/refuser-remise")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public DevisResponse refuserRemise(@PathVariable Long id) {
        return devisService.refuserRemise(id);
    }
}

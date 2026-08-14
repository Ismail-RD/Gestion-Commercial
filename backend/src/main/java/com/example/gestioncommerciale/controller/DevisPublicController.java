package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.DevisPublicResponse;
import com.example.gestioncommerciale.dto.ReponseClientRequest;
import com.example.gestioncommerciale.service.DevisPublicService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Espace client, accessible SANS authentification via le jeton du lien recu par
 * email. Expose uniquement la consultation du devis, son PDF, et la reponse
 * (acceptation avec bon de commande, ou refus).
 */
@RestController
@RequestMapping("/api/public/devis")
public class DevisPublicController {

    private final DevisPublicService devisPublicService;

    public DevisPublicController(DevisPublicService devisPublicService) {
        this.devisPublicService = devisPublicService;
    }

    @GetMapping("/{token}")
    public DevisPublicResponse consulter(@PathVariable String token) {
        return devisPublicService.consulter(token);
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        byte[] pdf = devisPublicService.pdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("devis.pdf").build().toString())
                .body(pdf);
    }

    /** Acceptation : le bon de commande est obligatoire. */
    @PostMapping(value = "/{token}/accepter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> accepter(@PathVariable String token,
                                         @RequestParam("bonCommande") MultipartFile bonCommande) {
        devisPublicService.accepter(token, bonCommande);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/refuser")
    public ResponseEntity<Void> refuser(@PathVariable String token,
                                        @RequestBody(required = false) ReponseClientRequest reponse) {
        devisPublicService.refuser(token, reponse != null ? reponse.commentaire() : null);
        return ResponseEntity.noContent().build();
    }
}

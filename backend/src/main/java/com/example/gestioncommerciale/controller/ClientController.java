package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.ClientRequest;
import com.example.gestioncommerciale.dto.ClientResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.PlafondCreditRequest;
import com.example.gestioncommerciale.dto.ReattributionRequest;
import com.example.gestioncommerciale.dto.filter.ClientFilter;
import com.example.gestioncommerciale.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.example.gestioncommerciale.security.Autorisations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les annotations ouvrent l'acces par role ; la propriete du portefeuille
 * (un commercial ne voit et ne gere que ses clients) est appliquee dans le
 * service, qui seul connait le titulaire de chaque fiche.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public PageResponse<ClientResponse> lister(ClientFilter filtre, Pageable pageable) {
        return clientService.lister(filtre, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public ClientResponse trouver(@PathVariable Long id) {
        return clientService.trouverParId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<ClientResponse> creer(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ClientResponse modifier(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.modifier(id, request);
    }

    /**
     * Reattribution du client a un autre commercial. Reservee a l'encadrement :
     * en usage normal, le commercial reste celui qui a saisi le client.
     */
    @PatchMapping("/{id}/commercial")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public ClientResponse reattribuer(@PathVariable Long id,
                                      @Valid @RequestBody ReattributionRequest request) {
        return clientService.reattribuer(id, request.commercialId());
    }

    /**
     * Definit ou retire le plafond de credit, apres la creation du client.
     * Decision d'encadrement : le commercial ne fixe pas lui-meme le credit.
     */
    @PostMapping("/{id}/plafond")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public ClientResponse definirPlafond(@PathVariable Long id,
                                         @Valid @RequestBody PlafondCreditRequest request) {
        return clientService.definirPlafond(id, request.plafondCredit());
    }

    /**
     * Debloque un client dont l'encours avait depasse le plafond. Decision
     * d'encadrement, pas un acte de saisie courant.
     */
    @PostMapping("/{id}/debloquer")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public ClientResponse debloquer(@PathVariable Long id) {
        return clientService.debloquer(id);
    }

    /** Blocage manuel d'un client (ex. litige). Reserve a l'encadrement. */
    @PostMapping("/{id}/bloquer")
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public ClientResponse bloquer(@PathVariable Long id) {
        return clientService.bloquer(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ECRIRE_COMMERCIAL)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        clientService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}

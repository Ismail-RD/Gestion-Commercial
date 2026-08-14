package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.DefinitionMotDePasseRequest;
import com.example.gestioncommerciale.dto.InvitationResponse;
import com.example.gestioncommerciale.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reponse a une invitation : l'invite n'a pas encore de compte utilisable, ces
 * routes sont donc ouvertes. La securite repose sur le jeton non devinable
 * present dans l'URL, a usage unique et expirant.
 */
@RestController
@RequestMapping("/api/public/invitations")
public class InvitationPublicController {

    private final InvitationService invitationService;

    public InvitationPublicController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Identite affichee sur la page, pour que l'invite sache a quoi il repond. */
    @GetMapping("/{token}")
    public InvitationResponse consulter(@PathVariable String token) {
        return invitationService.consulter(token);
    }

    @PostMapping("/{token}/mot-de-passe")
    public ResponseEntity<Void> definirMotDePasse(
            @PathVariable String token,
            @Valid @RequestBody DefinitionMotDePasseRequest request) {
        invitationService.definirMotDePasse(token, request.motDePasse());
        return ResponseEntity.noContent().build();
    }
}

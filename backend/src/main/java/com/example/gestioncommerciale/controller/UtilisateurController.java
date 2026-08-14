package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.ActivationRequest;
import com.example.gestioncommerciale.dto.InvitationRequest;
import com.example.gestioncommerciale.dto.UtilisateurModificationRequest;
import com.example.gestioncommerciale.dto.UtilisateurResponse;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.Autorisations;
import com.example.gestioncommerciale.service.InvitationService;
import com.example.gestioncommerciale.service.UtilisateurService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
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

import java.util.List;

/**
 * Comptes utilisateurs. La consultation alimente le choix du commercial en
 * charge d'un client ; la creation passe par une invitation, l'administrateur
 * ne choisit jamais le mot de passe de quelqu'un d'autre.
 */
@RestController
@RequestMapping("/api/utilisateurs")
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private final InvitationService invitationService;
    private final UtilisateurService utilisateurService;

    public UtilisateurController(UtilisateurRepository utilisateurRepository,
                                 InvitationService invitationService,
                                 UtilisateurService utilisateurService) {
        this.utilisateurRepository = utilisateurRepository;
        this.invitationService = invitationService;
        this.utilisateurService = utilisateurService;
    }

    @GetMapping
    @PreAuthorize(Autorisations.ENCADREMENT_COMMERCIAL)
    public List<UtilisateurResponse> lister(@RequestParam(required = false) Role role) {
        Sort tri = Sort.by("nom", "prenom");
        List<Utilisateur> utilisateurs = role != null
                ? utilisateurRepository.findByRole(role, tri)
                : utilisateurRepository.findAll(tri);
        return utilisateurs.stream().map(this::toResponse).toList();
    }

    /** Cree le compte et envoie a l'interesse le lien pour choisir son mot de passe. */
    @PostMapping
    @PreAuthorize(Autorisations.ADMINISTRER)
    public ResponseEntity<UtilisateurResponse> inviter(@Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(invitationService.inviter(request)));
    }

    /** Nouveau lien pour un invite qui n'a pas encore repondu (ou dont le lien a expire). */
    @PostMapping("/{id}/renvoyer-invitation")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public UtilisateurResponse renvoyerInvitation(@PathVariable Long id) {
        return toResponse(invitationService.renvoyer(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public UtilisateurResponse trouver(@PathVariable Long id) {
        return toResponse(utilisateurService.trouverParId(id));
    }

    /** Identite et role. Le mot de passe reste l'affaire de son titulaire. */
    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public UtilisateurResponse modifier(@PathVariable Long id,
                                        @Valid @RequestBody UtilisateurModificationRequest request) {
        return toResponse(utilisateurService.modifier(id, request));
    }

    /**
     * Retire l'acces sans effacer l'historique : c'est la sortie normale d'un
     * collaborateur qui quitte l'entreprise.
     */
    @PatchMapping("/{id}/activation")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public UtilisateurResponse changerActivation(@PathVariable Long id,
                                                 @Valid @RequestBody ActivationRequest request) {
        return toResponse(utilisateurService.changerActivation(id, request.actif()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        utilisateurService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    private UtilisateurResponse toResponse(Utilisateur u) {
        return new UtilisateurResponse(u.getId(), u.getNom(), u.getPrenom(),
                u.getEmail(), u.getRole(), u.isActif());
    }
}

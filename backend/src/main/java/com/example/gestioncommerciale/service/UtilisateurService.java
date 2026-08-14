package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.UtilisateurModificationRequest;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.ClientRepository;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Gestion des comptes par l'administrateur.
 *
 * <p>Deux precautions traversent ce service. D'abord, un administrateur ne peut
 * pas se retirer lui-meme ses moyens d'agir, ni laisser l'application sans
 * aucun administrateur actif : la porte se refermerait sur tout le monde.
 * Ensuite, un compte qui a laisse des traces (clients, documents, mouvements de
 * stock) ne s'efface pas ; il se desactive, pour que l'historique reste lisible.
 */
@Service
public class UtilisateurService {

    /** Roles auxquels un portefeuille client peut etre confie. */
    private static final Set<Role> ROLES_COMMERCIAUX =
            Set.of(Role.COMMERCIAL, Role.RESPONSABLE_COMMERCIAL);

    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final DevisRepository devisRepository;
    private final CommandeRepository commandeRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final CurrentUserService currentUserService;

    public UtilisateurService(UtilisateurRepository utilisateurRepository,
                              ClientRepository clientRepository,
                              DevisRepository devisRepository,
                              CommandeRepository commandeRepository,
                              MouvementStockRepository mouvementStockRepository,
                              CurrentUserService currentUserService) {
        this.utilisateurRepository = utilisateurRepository;
        this.clientRepository = clientRepository;
        this.devisRepository = devisRepository;
        this.commandeRepository = commandeRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Utilisateur trouverParId(Long id) {
        return getOrThrow(id);
    }

    @Transactional
    public Utilisateur modifier(Long id, UtilisateurModificationRequest request) {
        Utilisateur utilisateur = getOrThrow(id);

        if (utilisateurRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un autre compte utilise deja l'adresse " + request.email());
        }
        if (request.role() != utilisateur.getRole()) {
            exigerChangementDeRolePossible(utilisateur, request.role());
        }

        utilisateur.setNom(request.nom());
        utilisateur.setPrenom(request.prenom());
        utilisateur.setEmail(request.email());
        utilisateur.setRole(request.role());
        return utilisateurRepository.save(utilisateur);
    }

    @Transactional
    public void supprimer(Long id) {
        Utilisateur utilisateur = getOrThrow(id);
        exigerAutreQueSoi(utilisateur, "Vous ne pouvez pas supprimer votre propre compte");
        exigerAdminRestant(utilisateur);

        if (clientRepository.existsByCommercialId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce commercial a encore des clients : reattribuez son portefeuille, "
                            + "ou desactivez son compte pour lui retirer l'acces");
        }
        if (devisRepository.existsByCommercialId(id)
                || commandeRepository.existsByCommercialId(id)
                || mouvementStockRepository.existsByUtilisateurId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte figure sur des documents ou des mouvements de stock : "
                            + "desactivez-le plutot que de l'effacer, l'historique doit "
                            + "rester lisible");
        }
        utilisateurRepository.delete(utilisateur);
    }

    /**
     * Desactive ou reactive un compte. Un compte desactive garde tout son
     * historique mais ne peut plus se connecter : c'est la sortie normale d'un
     * collaborateur qui quitte l'entreprise.
     */
    @Transactional
    public Utilisateur changerActivation(Long id, boolean actif) {
        Utilisateur utilisateur = getOrThrow(id);
        exigerAutreQueSoi(utilisateur, actif
                ? "Votre compte est deja actif"
                : "Vous ne pouvez pas desactiver votre propre compte");

        if (!actif) {
            exigerAdminRestant(utilisateur);
        } else if (utilisateur.getMotDePasse() == null || utilisateur.getMotDePasse().isBlank()) {
            // Reactiver un invite qui n'a jamais repondu donnerait un compte sans
            // mot de passe, donc inutilisable : il lui faut une invitation.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte n'a pas encore de mot de passe : renvoyez-lui une invitation");
        }

        utilisateur.setActif(actif);
        return utilisateurRepository.save(utilisateur);
    }

    // --- Garde-fous ---

    private void exigerChangementDeRolePossible(Utilisateur utilisateur, Role nouveau) {
        if (utilisateur.getRole() == Role.ADMIN && estMoi(utilisateur)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous ne pouvez pas retirer votre propre role d'administrateur");
        }
        if (utilisateur.getRole() == Role.ADMIN) {
            exigerAdminRestant(utilisateur);
        }
        // La propriete d'un client suit son commercial : sortir son titulaire de
        // la force de vente rendrait le portefeuille invisible a tous.
        if (!ROLES_COMMERCIAUX.contains(nouveau)
                && clientRepository.existsByCommercialId(utilisateur.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte est titulaire d'un portefeuille client : reattribuez-le "
                            + "avant de lui donner un role hors de la force de vente");
        }
    }

    /** Refuse de retirer le dernier administrateur actif. */
    private void exigerAdminRestant(Utilisateur utilisateur) {
        if (utilisateur.getRole() != Role.ADMIN || !utilisateur.isActif()) {
            return;
        }
        long autresAdmins = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isActif())
                .filter(u -> !u.getId().equals(utilisateur.getId()))
                .count();
        if (autresAdmins == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "C'est le dernier administrateur actif : nommez-en un autre d'abord, "
                            + "sinon plus personne ne pourra administrer l'application");
        }
    }

    private void exigerAutreQueSoi(Utilisateur utilisateur, String message) {
        if (estMoi(utilisateur)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private boolean estMoi(Utilisateur utilisateur) {
        Utilisateur courant = currentUserService.getUtilisateurCourant();
        return courant != null && courant.getId().equals(utilisateur.getId());
    }

    private Utilisateur getOrThrow(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}

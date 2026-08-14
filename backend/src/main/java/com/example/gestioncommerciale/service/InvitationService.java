package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.InvitationRequest;
import com.example.gestioncommerciale.dto.InvitationResponse;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Creation d'un compte par invitation : l'administrateur saisit l'identite et
 * le role, l'interesse choisit lui-meme son mot de passe via un lien recu par
 * email.
 *
 * <p>Le compte reste inactif jusque-la, donc inutilisable meme si son adresse
 * est connue : {@code UtilisateurDetails.isEnabled()} refuse la connexion.
 */
@Service
public class InvitationService {

    /** Duree de vie du lien : assez large pour une absence, assez courte pour ne pas trainer. */
    private static final int JOURS_VALIDITE = 7;

    private final UtilisateurRepository utilisateurRepository;
    private final InvitationEmailService invitationEmailService;
    private final PasswordEncoder passwordEncoder;

    public InvitationService(UtilisateurRepository utilisateurRepository,
                             InvitationEmailService invitationEmailService,
                             PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.invitationEmailService = invitationEmailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cree le compte inactif et envoie le lien d'inscription.
     *
     * <p>L'envoi fait partie de la transaction : si l'email ne part pas, le
     * compte n'est pas cree. Mieux vaut rien qu'un compte inaccessible dont
     * personne ne sait qu'il attend une invitation.
     */
    @Transactional
    public Utilisateur inviter(InvitationRequest request) {
        if (utilisateurRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte existe deja avec l'adresse " + request.email());
        }
        Utilisateur invite = Utilisateur.builder()
                .nom(request.nom())
                .prenom(request.prenom())
                .email(request.email())
                .role(request.role())
                .actif(false)
                .build();
        armerInvitation(invite);
        Utilisateur enregistre = utilisateurRepository.save(invite);
        invitationEmailService.envoyer(enregistre);
        return enregistre;
    }

    /**
     * Renvoie l'invitation avec un jeton neuf. L'ancien lien cesse aussitot de
     * fonctionner : un lien qui traine dans une boite mail ne doit pas rouvrir
     * un compte des mois plus tard. Si l'email ne part pas, le nouveau jeton est
     * annule et l'ancien reste donc valable.
     */
    @Transactional
    public Utilisateur renvoyer(Long id) {
        Utilisateur invite = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        if (invite.isActif()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte est deja actif : son titulaire a defini son mot de passe");
        }
        armerInvitation(invite);
        Utilisateur enregistre = utilisateurRepository.save(invite);
        invitationEmailService.envoyer(enregistre);
        return enregistre;
    }

    /** Identite affichee sur la page d'inscription, avant la saisie. */
    @Transactional(readOnly = true)
    public InvitationResponse consulter(String token) {
        Utilisateur invite = exigerInvitationValide(token);
        return new InvitationResponse(invite.getNom(), invite.getPrenom(),
                invite.getEmail(), invite.getRole());
    }

    /**
     * L'invite choisit son mot de passe : le compte s'active et le jeton est
     * efface, le lien ne peut donc pas servir deux fois.
     */
    @Transactional
    public void definirMotDePasse(String token, String motDePasse) {
        Utilisateur invite = exigerInvitationValide(token);
        invite.setMotDePasse(passwordEncoder.encode(motDePasse));
        invite.setActif(true);
        invite.setTokenInvitation(null);
        invite.setInvitationExpireLe(null);
        utilisateurRepository.save(invite);
    }

    private void armerInvitation(Utilisateur invite) {
        invite.setTokenInvitation(UUID.randomUUID().toString().replace("-", ""));
        invite.setInvitationExpireLe(LocalDateTime.now().plusDays(JOURS_VALIDITE));
    }

    private Utilisateur exigerInvitationValide(String token) {
        Utilisateur invite = utilisateurRepository.findByTokenInvitation(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cette invitation n'existe pas ou a deja ete utilisee"));
        if (invite.getInvitationExpireLe() == null
                || invite.getInvitationExpireLe().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Cette invitation a expire : demandez a l'administrateur de vous la renvoyer");
        }
        return invite;
    }
}

package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.NotificationResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.entity.Notification;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.NotificationRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Notifications de l'utilisateur connecte.
 *
 * <p>Aucun endpoint ne prend d'identifiant d'utilisateur : on ne lit que les
 * siennes. Il n'y a donc rien a autoriser par role -- seulement a verifier que
 * la notification demandee appartient bien au demandeur.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationRepository notificationRepository,
                                  CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> lister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long moi = currentUserService.getUtilisateurCourant().getId();
        return PageResponse.from(notificationRepository
                .findByDestinataireId(moi,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreation")))
                .map(this::toResponse));
    }

    /** Compteur de la cloche : sollicite en boucle, il doit rester une seule requete. */
    @GetMapping("/non-lues")
    public long compterNonLues() {
        return notificationRepository.countByDestinataireIdAndDateLectureIsNull(
                currentUserService.getUtilisateurCourant().getId());
    }

    @PostMapping("/{id}/lue")
    @Transactional
    public NotificationResponse marquerLue(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        Long moi = currentUserService.getUtilisateurCourant().getId();
        if (!notification.getDestinataire().getId().equals(moi)) {
            // 404 plutot que 403 : l'existence de la notification d'autrui ne
            // regarde pas le demandeur.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable");
        }
        if (!notification.estLue()) {
            notification.setDateLecture(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @PostMapping("/toutes-lues")
    @Transactional
    public ResponseEntity<Void> marquerToutLu() {
        notificationRepository.marquerToutLu(
                currentUserService.getUtilisateurCourant().getId(), LocalDateTime.now());
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getNiveau(),
                n.getTitre(),
                n.getMessage(),
                n.getTypeDocument(),
                n.getDocumentId(),
                n.getDateCreation(),
                n.getDateLecture());
    }
}

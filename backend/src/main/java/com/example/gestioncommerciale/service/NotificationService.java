package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.Notification;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.NotificationRepository;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emission des notifications.
 *
 * <p>Trois regles gouvernent tout ce fichier, et elles sont ce qui separe une
 * cloche utile d'une cloche qu'on finit par ignorer.
 *
 * <p><b>L'adressage est nominatif.</b> Une notification appartient a quelqu'un.
 * Rien n'est diffuse a tout le monde : le devis accepte va au commercial qui
 * l'a signe, pas a l'equipe.
 *
 * <p><b>On ne se notifie pas soi-meme.</b> Celui qui valide une remise sait
 * qu'il vient de la valider ; lui envoyer l'information ferait du volume sans
 * information.
 *
 * <p><b>Le responsable commercial suit son equipe.</b> Toute notification
 * destinee a un commercial lui est repercutee : il pilote la vente, il doit
 * voir passer ce qui arrive a ses vendeurs sans avoir a le demander.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(NotificationRepository notificationRepository,
                               UtilisateurRepository utilisateurRepository,
                               CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Contenu d'une notification, independamment de qui la recoit : le meme
     * evenement part souvent a plusieurs personnes.
     */
    public record Alerte(
            TypeNotification type,
            NiveauNotification niveau,
            String titre,
            String message,
            TypeDocument typeDocument,
            Long documentId,
            /** Cle d'idempotence des alertes recurrentes ; nulle pour un evenement ponctuel. */
            String cle
    ) {
        public Alerte(TypeNotification type, NiveauNotification niveau, String titre,
                      String message, TypeDocument typeDocument, Long documentId) {
            this(type, niveau, titre, message, typeDocument, documentId, null);
        }
    }

    /** Notifie une personne designee. */
    public void a(Utilisateur destinataire, Alerte alerte) {
        envoyer(List.of(destinataire), alerte);
    }

    /**
     * Notifie un commercial et, avec lui, l'encadrement commercial. C'est la
     * demande explicite : le responsable voit tout ce qui touche son equipe.
     */
    public void auCommercial(Utilisateur commercial, Alerte alerte) {
        List<Utilisateur> destinataires = new ArrayList<>();
        if (commercial != null) {
            destinataires.add(commercial);
        }
        destinataires.addAll(actifs(Role.RESPONSABLE_COMMERCIAL));
        envoyer(destinataires, alerte);
    }

    /** Notifie tous les titulaires actifs d'un ou plusieurs roles. */
    public void auxRoles(Alerte alerte, Role... roles) {
        List<Utilisateur> destinataires = new ArrayList<>();
        for (Role role : roles) {
            destinataires.addAll(actifs(role));
        }
        envoyer(destinataires, alerte);
    }

    // --- Lecture ---

    @Transactional(readOnly = true)
    public long compterNonLues(Long utilisateurId) {
        return notificationRepository.countByDestinataireIdAndDateLectureIsNull(utilisateurId);
    }

    // --- Interne ---

    private List<Utilisateur> actifs(Role role) {
        return utilisateurRepository.findByRole(role, Sort.by("nom")).stream()
                .filter(Utilisateur::isActif)
                .toList();
    }

    /**
     * Enregistre la notification pour chaque destinataire, en ecartant l'auteur
     * de l'action et les doublons.
     */
    private void envoyer(List<Utilisateur> destinataires, Alerte alerte) {
        Utilisateur auteur = auteurCourant();
        // Une meme personne peut apparaitre deux fois : un responsable
        // commercial qui suit un de ses propres dossiers, par exemple.
        Map<Long, Utilisateur> uniques = new LinkedHashMap<>();
        for (Utilisateur u : destinataires) {
            if (u == null || !u.isActif()) {
                continue;
            }
            if (auteur != null && auteur.getId().equals(u.getId())) {
                continue;
            }
            uniques.putIfAbsent(u.getId(), u);
        }

        for (Utilisateur destinataire : uniques.values()) {
            enregistrer(destinataire, alerte);
        }
    }

    private void enregistrer(Utilisateur destinataire, Alerte alerte) {
        if (alerte.cle() != null
                && notificationRepository.existsByDestinataireIdAndCle(
                        destinataire.getId(), alerte.cle())) {
            return;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .destinataire(destinataire)
                    .type(alerte.type())
                    .niveau(alerte.niveau())
                    .titre(alerte.titre())
                    .message(alerte.message())
                    .typeDocument(alerte.typeDocument())
                    .documentId(alerte.documentId())
                    .cle(alerte.cle())
                    .build());
        } catch (DataIntegrityViolationException doublon) {
            // Deux executions concurrentes du balayage ont vise la meme alerte :
            // l'index unique a tranche, et c'est exactement ce qu'on voulait.
        }
    }

    /**
     * Auteur de l'action en cours, ou {@code null} quand il n'y en a pas :
     * balayage nocturne, ou reponse d'un client depuis son lien public. Ces
     * deux cas sont normaux, ils ne doivent pas faire echouer l'envoi.
     */
    private Utilisateur auteurCourant() {
        try {
            return currentUserService.getUtilisateurCourant();
        } catch (RuntimeException aucunUtilisateur) {
            return null;
        }
    }
}

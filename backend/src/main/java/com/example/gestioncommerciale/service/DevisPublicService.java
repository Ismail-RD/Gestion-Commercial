package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.dto.DevisPublicResponse;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.ReponseClient;
import com.example.gestioncommerciale.repository.DevisRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Acces au devis par le client via son lien personnel (jeton), sans compte.
 *
 * IMPORTANT : la reponse du client est seulement enregistree (trace + bon de
 * commande). Elle ne modifie jamais le statut du devis : l'acceptation reste
 * une decision manuelle prise dans l'application apres verification.
 */
@Service
public class DevisPublicService {

    private final DevisRepository devisRepository;
    private final FileStorageService fileStorageService;
    private final DevisPdfService devisPdfService;
    private final SocieteProperties societe;
    private final NotificationService notifications;

    public DevisPublicService(DevisRepository devisRepository,
                              FileStorageService fileStorageService,
                              DevisPdfService devisPdfService,
                              SocieteProperties societe,
                              NotificationService notifications) {
        this.devisRepository = devisRepository;
        this.fileStorageService = fileStorageService;
        this.devisPdfService = devisPdfService;
        this.societe = societe;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public DevisPublicResponse consulter(String token) {
        Devis devis = parToken(token);
        Client c = devis.getClient();
        return new DevisPublicResponse(
                devis.getNumero(),
                devis.getReference(),
                devis.getDateCreation(),
                devis.getDateValidite(),
                devis.getMontantHT(),
                devis.getMontantTTC(),
                nomClient(c),
                societe.nom(),
                devis.getReponseClient(),
                devis.getDateReponseClient(),
                devis.getBonCommande() != null);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(String token) {
        return devisPdfService.genererDevisPdf(parToken(token).getId());
    }

    /**
     * Le client accepte : le bon de commande est obligatoire. Le statut du devis
     * reste inchange, seule la reponse est tracee.
     */
    @Transactional
    public void accepter(String token, MultipartFile bonCommande) {
        Devis devis = parToken(token);
        exigerSansReponse(devis);
        if (bonCommande == null || bonCommande.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le bon de commande est obligatoire pour accepter le devis");
        }
        String chemin = fileStorageService.stocker(bonCommande, "bon-commande-" + devis.getId());
        devis.setBonCommande(chemin);
        devis.setReponseClient(ReponseClient.ACCEPTE);
        devis.setDateReponseClient(LocalDateTime.now());
        devisRepository.save(devis);

        // Le commercial n'est pas devant son ecran quand le client repond : sans
        // notification, il ne l'apprendrait qu'en rouvrant la fiche par hasard.
        notifications.auCommercial(devis.getCommercial(), new NotificationService.Alerte(
                TypeNotification.DEVIS_ACCEPTE, NiveauNotification.URGENT,
                "Devis " + devis.getNumero() + " accepte par le client",
                nomClient(devis.getClient()) + " a accepte et depose son bon de commande. "
                        + "La commande peut etre creee.",
                TypeDocument.DEVIS, devis.getId()));
    }

    /** Le client refuse : on trace la reponse et son commentaire eventuel. */
    @Transactional
    public void refuser(String token, String commentaire) {
        Devis devis = parToken(token);
        exigerSansReponse(devis);
        devis.setReponseClient(ReponseClient.REFUSE);
        devis.setDateReponseClient(LocalDateTime.now());
        if (commentaire != null && !commentaire.isBlank()) {
            devis.setCommentaireClient(commentaire.trim());
        }
        devisRepository.save(devis);

        notifications.auCommercial(devis.getCommercial(), new NotificationService.Alerte(
                TypeNotification.DEVIS_REFUSE, NiveauNotification.ALERTE,
                "Devis " + devis.getNumero() + " refuse par le client",
                nomClient(devis.getClient()) + " a refuse le devis"
                        + (devis.getCommentaireClient() != null
                                ? " : " + devis.getCommentaireClient() : "."),
                TypeDocument.DEVIS, devis.getId()));
    }

    /** Bon de commande depose par le client, pour verification cote gestion. */
    @Transactional(readOnly = true)
    public Resource bonCommande(Long devisId) {
        Devis devis = devisRepository.findById(devisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Devis introuvable"));
        if (devis.getBonCommande() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucun bon de commande n'a ete depose pour ce devis");
        }
        return fileStorageService.charger(devis.getBonCommande());
    }

    /** Nom du fichier stocke (pour deduire le type au telechargement). */
    @Transactional(readOnly = true)
    public String nomBonCommande(Long devisId) {
        return devisRepository.findById(devisId).map(Devis::getBonCommande).orElse(null);
    }

    private Devis parToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien invalide");
        }
        return devisRepository.findByTokenClient(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lien invalide ou expire"));
    }

    /** Une seule reponse possible par lien. */
    private void exigerSansReponse(Devis devis) {
        if (devis.getReponseClient() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous avez deja repondu a ce devis le "
                            + (devis.getDateReponseClient() != null
                            ? devis.getDateReponseClient().toLocalDate() : ""));
        }
    }

    private String nomClient(Client c) {
        // Relation LAZY : materialiser le proxy avant instanceof.
        Client client = (Client) org.hibernate.Hibernate.unproxy(c);
        if (client instanceof ClientEntreprise e && e.getRaisonSociale() != null) {
            return e.getRaisonSociale();
        }
        if (client instanceof ClientParticulier p && p.getPrenom() != null) {
            return (p.getPrenom() + " " + client.getNom()).trim();
        }
        return client.getNom();
    }
}

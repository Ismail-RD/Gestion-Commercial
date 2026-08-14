package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.DevisRequest;
import com.example.gestioncommerciale.dto.DevisResponse;
import com.example.gestioncommerciale.dto.LigneDevisRequest;
import com.example.gestioncommerciale.dto.LigneDevisResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ReponseClientRequest;
import com.example.gestioncommerciale.dto.filter.DevisFilter;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.LigneDevis;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutDevis;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.ClientRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.example.gestioncommerciale.service.DevisService;
import com.example.gestioncommerciale.service.NotificationService;
import com.example.gestioncommerciale.service.NumeroDocument;
import com.example.gestioncommerciale.service.PolitiquePouvoirs;
import com.example.gestioncommerciale.specification.DevisSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@Transactional
public class DevisServiceImpl implements DevisService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final DevisRepository devisRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final CurrentUserService currentUserService;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final NotificationService notifications;

    public DevisServiceImpl(DevisRepository devisRepository,
                            ClientRepository clientRepository,
                            ProduitRepository produitRepository,
                            CurrentUserService currentUserService,
                            PolitiquePouvoirs politiquePouvoirs,
                            NotificationService notifications) {
        this.devisRepository = devisRepository;
        this.clientRepository = clientRepository;
        this.produitRepository = produitRepository;
        this.currentUserService = currentUserService;
        this.politiquePouvoirs = politiquePouvoirs;
        this.notifications = notifications;
    }

    @Override
    public DevisResponse creer(DevisRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
        // Un commercial ne travaille que pour ses propres clients.
        exigerAcces(client);
        // Chiffrer pour un client bloque n'a pas de sens : le devis ne pourrait
        // pas devenir commande. Mieux vaut le refuser a la saisie que laisser le
        // commercial negocier une affaire qui butera au moment de la conclure.
        exigerClientNonBloque(client);
        Utilisateur commercial = currentUserService.getUtilisateurCourant();

        Devis devis = Devis.builder()
                .numero(genererNumero())
                .reference(request.reference())
                .dateValidite(request.dateValidite())
                .statut(StatutDevis.BROUILLON)
                .client(client)
                .commercial(commercial)
                .build();

        remplirLignes(devis, request.lignes());
        calculerMontants(devis);
        boolean arbitrageAttendu = arbitrerRemise(devis);

        Devis enregistre = devisRepository.save(devis);
        if (arbitrageAttendu) {
            notifierArbitrageAttendu(enregistre);
        }
        return toResponse(enregistre);
    }

    @Override
    public DevisResponse modifier(Long id, DevisRequest request) {
        Devis devis = getOrThrow(id);
        // Un devis en attente de validation reste un brouillon a cet egard : le
        // commercial doit pouvoir revoir sa remise a la baisse sans attendre la
        // reponse de l'encadrement.
        if (devis.getStatut() != StatutDevis.BROUILLON
                && devis.getStatut() != StatutDevis.EN_ATTENTE_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un devis au statut BROUILLON peut etre modifie");
        }
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
        // Un commercial ne travaille que pour ses propres clients.
        exigerAcces(client);
        // Un devis existant reste corrigeable meme si son client vient d'etre
        // bloque ; en revanche on ne le bascule pas vers un client bloque, ce
        // qui reviendrait a contourner le refus a la creation.
        if (!client.getId().equals(devis.getClient().getId())) {
            exigerClientNonBloque(client);
        }

        devis.setClient(client);
        devis.setReference(request.reference());
        devis.setDateValidite(request.dateValidite());
        devis.viderLignes();
        remplirLignes(devis, request.lignes());
        calculerMontants(devis);

        boolean arbitrageAttendu = arbitrerRemise(devis);

        Devis enregistre = devisRepository.save(devis);
        if (arbitrageAttendu) {
            notifierArbitrageAttendu(enregistre);
        }
        return toResponse(enregistre);
    }

    /**
     * Place le devis en attente de validation des que la remise depasse le
     * seuil, et l'en sort quand elle redescend. Meme regle que sur la commande :
     * la demande d'aval part a la saisie, sans attendre un geste du commercial.
     * Sinon l'envoi resterait propose sur un devis que personne n'a arbitre.
     *
     * <p>Chacun est juge de ce qu'il accorde dans la limite de son propre seuil :
     * au-dela, meme un responsable commercial doit en referer.
     */
    private boolean arbitrerRemise(Devis devis) {
        if (devis.getStatut() != StatutDevis.BROUILLON
                && devis.getStatut() != StatutDevis.EN_ATTENTE_VALIDATION) {
            return false;
        }
        // Les lignes viennent de changer : un accord anterieur portait sur une
        // autre remise, il ne vaut plus.
        StatutDevis avant = devis.getStatut();
        boolean couvert = !politiquePouvoirs.depassePouvoirDe(
                currentUserService.getUtilisateurCourant(), devis);
        devis.setRemiseValidee(couvert);
        devis.setStatut(couvert
                ? StatutDevis.BROUILLON : StatutDevis.EN_ATTENTE_VALIDATION);

        // Une demande d'arbitrage nait ici, mais elle ne se notifie qu'apres
        // l'enregistrement : a la creation, le devis n'a pas encore d'identifiant
        // et la notification renverrait vers rien. Une remise deja en attente
        // qu'on retouche encore ne renotifie pas.
        return !couvert && avant != StatutDevis.EN_ATTENTE_VALIDATION;
    }

    /** Previent l'encadrement qu'une remise attend son aval. */
    private void notifierArbitrageAttendu(Devis devis) {
        notifications.auxRoles(new NotificationService.Alerte(
                        TypeNotification.REMISE_A_VALIDER, NiveauNotification.ALERTE,
                        "Remise a arbitrer sur " + devis.getNumero(),
                        "Remise de " + pourcentage(politiquePouvoirs.remiseMax(devis))
                                + " % demandee pour " + nomClient(devis.getClient())
                                + ". Le devis ne peut ni partir ni etre imprime sans votre aval.",
                        TypeDocument.DEVIS, devis.getId()),
                Role.RESPONSABLE_COMMERCIAL, Role.ADMIN);
    }

    /** Pourcentage lisible dans un message : 15 plutot que 15.00. */
    private String pourcentage(BigDecimal valeur) {
        return valeur == null ? "0" : valeur.stripTrailingZeros().toPlainString();
    }

    @Override
    @Transactional(readOnly = true)
    public DevisResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DevisResponse> lister(DevisFilter filtre, Pageable pageable) {
        Specification<Devis> spec = DevisSpecifications.avecFiltre(filtre);
        // Un commercial ne voit que les documents de ses propres clients.
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction != null) {
            spec = spec.and((root, q, cb) -> cb.equal(
                    root.get("client").get("commercial").get("id"), restriction));
        }
        return PageResponse.from(
                devisRepository.findAll(spec, pageable)
                        .map(this::toResponse));
    }

    @Override
    public void supprimer(Long id) {
        Devis devis = getOrThrow(id);
        if (devis.getStatut() == StatutDevis.ACCEPTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un devis accepte ne peut pas etre supprime");
        }
        devisRepository.delete(devis);
    }

    // --- Workflow ---

    @Override
    public DevisResponse envoyer(Long id) {
        Devis devis = getOrThrow(id);
        exigerStatut(devis, StatutDevis.BROUILLON, "envoye");
        // La remise excessive met le devis en attente des la saisie : un
        // brouillon qui en porte encore une sort d'un refus de l'encadrement,
        // il doit etre revu a la baisse et non renvoye tel quel.
        if (politiquePouvoirs.validationAttendue(devis)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de ce devis a ete refusee : revoyez-la a la baisse "
                            + "avant de le renvoyer");
        }
        devis.setStatut(StatutDevis.ENVOYE);
        // Premier envoi seulement : un renvoi apres correction ne doit pas
        // effacer la date a laquelle le prix est devenu opposable au client.
        if (devis.getDateEnvoi() == null) {
            devis.setDateEnvoi(LocalDateTime.now());
        }
        return toResponse(devisRepository.save(devis));
    }

    @Override
    public DevisResponse validerRemise(Long id) {
        Devis devis = getOrThrow(id);
        exigerStatut(devis, StatutDevis.EN_ATTENTE_VALIDATION, "valide");
        exigerPouvoirDeValider(politiquePouvoirs.remiseMax(devis));
        // L'aval ne fait pas avancer le devis : il rouvre l'envoi, l'impression
        // et la transmission au client. Le devis reste un brouillon et c'est au
        // commercial de decider quand il part.
        devis.setRemiseValidee(true);
        devis.setStatut(StatutDevis.BROUILLON);
        devis.setDateValidationRemise(LocalDateTime.now());

        // Le commercial attendait cette reponse pour pouvoir envoyer : sans
        // notification, il la decouvrirait en rouvrant sa fiche par hasard.
        notifications.auCommercial(devis.getCommercial(), new NotificationService.Alerte(
                TypeNotification.REMISE_VALIDEE, NiveauNotification.INFORMATION,
                "Remise validee sur " + devis.getNumero(),
                "Votre remise de " + pourcentage(politiquePouvoirs.remiseMax(devis))
                        + " % est acceptee : le devis peut partir chez le client.",
                TypeDocument.DEVIS, devis.getId()));
        return toResponse(devisRepository.save(devis));
    }

    @Override
    public DevisResponse refuserRemise(Long id) {
        Devis devis = getOrThrow(id);
        exigerStatut(devis, StatutDevis.EN_ATTENTE_VALIDATION, "refuse");
        // Retour au brouillon : le commercial doit revoir sa remise a la baisse.
        devis.setRemiseValidee(false);
        devis.setStatut(StatutDevis.BROUILLON);
        // Un refus est un arbitrage comme un autre : il se date.
        devis.setDateValidationRemise(LocalDateTime.now());

        notifications.auCommercial(devis.getCommercial(), new NotificationService.Alerte(
                TypeNotification.REMISE_REFUSEE, NiveauNotification.ALERTE,
                "Remise refusee sur " + devis.getNumero(),
                "La remise demandee n'a pas ete accordee : revoyez-la a la baisse "
                        + "avant de renvoyer le devis.",
                TypeDocument.DEVIS, devis.getId()));
        return toResponse(devisRepository.save(devis));
    }

    /**
     * Un validateur ne couvre pas au-dela de son propre seuil : la remise
     * remonte alors a l'administrateur, qui n'en a pas.
     */
    private void exigerPouvoirDeValider(BigDecimal remise) {
        Utilisateur validateur = currentUserService.getUtilisateurCourant();
        if (politiquePouvoirs.depassePouvoirDe(validateur, remise)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette remise de " + remise.stripTrailingZeros().toPlainString()
                            + " % depasse votre seuil de "
                            + politiquePouvoirs.seuilDe(validateur).stripTrailingZeros().toPlainString()
                            + " % : seul un administrateur peut la valider");
        }
    }

    @Override
    public DevisResponse accepter(Long id, ReponseClientRequest reponse) {
        return repondreClient(id, StatutDevis.ACCEPTE, reponse);
    }

    @Override
    public DevisResponse refuser(Long id, ReponseClientRequest reponse) {
        return repondreClient(id, StatutDevis.REFUSE, reponse);
    }

    private DevisResponse repondreClient(Long id, StatutDevis nouveauStatut, ReponseClientRequest reponse) {
        Devis devis = getOrThrow(id);
        exigerStatut(devis, StatutDevis.ENVOYE,
                nouveauStatut == StatutDevis.ACCEPTE ? "accepte" : "refuse");
        devis.setStatut(nouveauStatut);
        devis.setDateReponseClient(LocalDateTime.now());
        if (reponse != null) {
            devis.setCommentaireClient(reponse.commentaire());
        }
        return toResponse(devisRepository.save(devis));
    }

    // --- Helpers ---

    private Devis getOrThrow(Long id) {
        Devis document = devisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Devis", id));
        exigerAcces(document.getClient());
        return document;
    }

    /**
     * Meme regle que sur la commande : un client dont l'encours a depasse le
     * plafond ne recoit plus rien tant qu'un administrateur ne l'a pas debloque.
     */
    private void exigerClientNonBloque(com.example.gestioncommerciale.entity.Client client) {
        if (client.estBloque()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Client bloque : plafond de credit depasse. Deblocage requis avant tout devis.");
        }
    }

    /**
     * Un commercial n'accede qu'aux documents de ses clients : sans ce garde,
     * un identifiant devine suffirait a consulter le dossier d'un collegue.
     */
    private void exigerAcces(com.example.gestioncommerciale.entity.Client client) {
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction == null) {
            return;
        }
        Long titulaire = client != null && client.getCommercial() != null
                ? client.getCommercial().getId() : null;
        if (!restriction.equals(titulaire)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce document appartient a un autre commercial");
        }
    }

    private void exigerStatut(Devis devis, StatutDevis attendu, String action) {
        if (devis.getStatut() != attendu) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un devis au statut " + devis.getStatut() + " ne peut pas etre " + action
                            + " (statut requis : " + attendu + ")");
        }
    }

    private void remplirLignes(Devis devis, List<LigneDevisRequest> lignes) {
        for (LigneDevisRequest req : lignes) {
            Produit produit = produitRepository.findById(req.produitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", req.produitId()));

            BigDecimal remise = req.remise() != null ? req.remise() : BigDecimal.ZERO;

            // Prix negocie s'il est fourni, sinon prix catalogue. Dans tous les cas
            // la valeur est figee sur la ligne : les evolutions futures du catalogue
            // ne modifient pas un devis deja etabli.
            //
            // Un commercial ne fixe ni le prix ni la TVA : le catalogue s'impose,
            // meme si la requete porte d'autres valeurs. Sa marge de negociation
            // passe par la remise, encadree par le seuil de validation.
            boolean imposes = currentUserService.prixImposes();
            BigDecimal prixUnitaire = (!imposes && req.prixUnitaire() != null)
                    ? req.prixUnitaire()
                    : produit.getPrixUnitaireHT();
            BigDecimal tauxTVA = (!imposes && req.tauxTVA() != null)
                    ? req.tauxTVA()
                    : produit.getTauxTVA();

            LigneDevis ligne = LigneDevis.builder()
                    .produit(produit)
                    .designation(produit.getDesignation())
                    .quantite(req.quantite())
                    .prixUnitaire(prixUnitaire)
                    .tauxTVA(tauxTVA)
                    .remise(remise)
                    .montantLigne(montantLigneHT(prixUnitaire, req.quantite(), remise))
                    .build();

            devis.ajouterLigne(ligne);
        }
    }

    /**
     * Montant HT d'une ligne, remise (en %) deduite.
     */
    private BigDecimal montantLigneHT(BigDecimal prixUnitaire, BigDecimal quantite, BigDecimal remisePct) {
        BigDecimal brut = prixUnitaire.multiply(quantite);
        BigDecimal facteurRemise = BigDecimal.ONE.subtract(remisePct.divide(CENT));
        return brut.multiply(facteurRemise).setScale(2, RoundingMode.HALF_UP);
    }

    private void calculerMontants(Devis devis) {
        BigDecimal totalHT = BigDecimal.ZERO;
        BigDecimal totalTVA = BigDecimal.ZERO;
        for (LigneDevis ligne : devis.getLignes()) {
            totalHT = totalHT.add(ligne.getMontantLigne());
            BigDecimal tva = ligne.getMontantLigne()
                    .multiply(ligne.getTauxTVA()).divide(CENT);
            totalTVA = totalTVA.add(tva);
        }
        totalHT = totalHT.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTTC = totalHT.add(totalTVA).setScale(2, RoundingMode.HALF_UP);
        devis.setMontantHT(totalHT);
        devis.setMontantTTC(totalTTC);
    }

    private String genererNumero() {
        String prefix = "DEV-" + Year.now().getValue() + "-";
        return NumeroDocument.suivant(prefix, devisRepository.dernierNumero(prefix));
    }

    private DevisResponse toResponse(Devis d) {
        Client c = d.getClient();
        Utilisateur com = d.getCommercial();
        List<LigneDevisResponse> lignes = d.getLignes().stream()
                .map(this::toLigneResponse)
                .toList();
        return new DevisResponse(
                d.getId(),
                d.getNumero(),
                d.getReference(),
                d.getDateCreation(),
                d.getDateValidite(),
                d.getStatut(),
                d.getDateEnvoi(),
                d.getDateValidationRemise(),
                d.getMontantHT(),
                d.getMontantTTC(),
                d.getDateReponseClient(),
                d.getCommentaireClient(),
                d.getDateEnvoiEmail(),
                d.getReponseClient(),
                d.getBonCommande() != null,
                politiquePouvoirs.validationAttendue(d),
                c != null ? c.getId() : null,
                c != null ? nomClient(c) : null,
                com != null ? com.getId() : null,
                com != null ? com.getPrenom() + " " + com.getNom() : null,
                lignes
        );
    }

    private LigneDevisResponse toLigneResponse(LigneDevis l) {
        return new LigneDevisResponse(
                l.getId(),
                l.getProduit() != null ? l.getProduit().getId() : null,
                l.getProduit() != null ? l.getProduit().getReference() : null,
                l.getDesignation(),
                l.getQuantite(),
                l.getPrixUnitaire(),
                l.getTauxTVA(),
                l.getRemise(),
                l.getMontantLigne()
        );
    }

    private String nomClient(Client c) {
        String prenom = null;
        if (c instanceof com.example.gestioncommerciale.entity.ClientParticulier p) {
            prenom = p.getPrenom();
        }
        return prenom != null ? prenom + " " + c.getNom() : c.getNom();
    }
}

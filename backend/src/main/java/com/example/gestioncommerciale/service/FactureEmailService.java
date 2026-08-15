package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.service.email.ExpediteurEmail;
import com.example.gestioncommerciale.service.email.MessageEmail;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Envoie la facture au client par email, PDF en piece jointe.
 *
 * Contrairement au devis, aucune action n'est attendue du client dans
 * l'application : l'email est purement informatif, le reglement se constate
 * ensuite par la saisie d'un paiement.
 */
@Service
public class FactureEmailService {

    private static final Logger log = LoggerFactory.getLogger(FactureEmailService.class);

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FactureRepository factureRepository;
    private final FacturePdfService facturePdfService;
    private final SpringTemplateEngine templateEngine;
    private final ExpediteurEmail expedition;
    private final SocieteProperties societe;
    private final String expediteur;
    private final DecimalFormat montantFormat;

    public FactureEmailService(FactureRepository factureRepository,
                               FacturePdfService facturePdfService,
                               SpringTemplateEngine templateEngine,
                               ExpediteurEmail expedition,
                               SocieteProperties societe,
                               @Value("${app.mail.expediteur}") String expediteur) {
        this.factureRepository = factureRepository;
        this.facturePdfService = facturePdfService;
        this.templateEngine = templateEngine;
        this.expedition = expedition;
        this.societe = societe;
        this.expediteur = expediteur;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    @Transactional
    public void envoyerAuClient(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", factureId));

        Client client = (Client) Hibernate.unproxy(facture.getClient());
        String destinataire = client.getEmail();
        if (destinataire == null || destinataire.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce client n'a pas d'adresse email : impossible de lui envoyer la facture");
        }

        byte[] pdf = facturePdfService.genererFacturePdf(factureId);
        String html = construireCorps(facture, client);

        try {
            expedition.envoyer(new MessageEmail(destinataire,
                    "Facture " + facture.getNumero() + " - " + societe.nom(), html,
                    List.of(MessageEmail.PieceJointe.pdf(
                            "facture-" + facture.getNumero() + ".pdf", pdf))));
        } catch (MailException e) {
            log.error("Envoi de la facture {} a {} impossible (expediteur {}) : {}",
                    facture.getNumero(), destinataire, expediteur, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envoi de l'email impossible : verifiez la configuration des emails");
        }

        facture.setDateEnvoiEmail(LocalDateTime.now());
        factureRepository.save(facture);
    }

    private String construireCorps(Facture facture, Client client) {
        BigDecimal ttc = valeur(facture.getMontantTTC());
        BigDecimal paye = valeur(facture.getMontantPaye());
        BigDecimal reste = ttc.subtract(paye);

        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoSrc", expedition.sourceLogo());
        ctx.setVariable("clientNom", nomClient(client));
        ctx.setVariable("numero", facture.getNumero());
        ctx.setVariable("commandeNumero", facture.getCommande() != null
                ? facture.getCommande().getNumero() : "");
        ctx.setVariable("date", facture.getDateFacture() != null
                ? facture.getDateFacture().format(DATE_FR) : "");
        ctx.setVariable("echeance", facture.getDateEcheance() != null
                ? facture.getDateEcheance().format(DATE_FR) : "");
        ctx.setVariable("netAPayer", montantFormat.format(ttc));
        ctx.setVariable("resteAPayer", montantFormat.format(reste));
        ctx.setVariable("soldee", reste.signum() <= 0);
        return templateEngine.process("email-facture", ctx);
    }

    private BigDecimal valeur(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String nomClient(Client c) {
        if (c instanceof ClientEntreprise e && e.getRaisonSociale() != null) {
            return e.getRaisonSociale();
        }
        if (c instanceof ClientParticulier p && p.getPrenom() != null) {
            return (p.getPrenom() + " " + c.getNom()).trim();
        }
        return c.getNom();
    }
}

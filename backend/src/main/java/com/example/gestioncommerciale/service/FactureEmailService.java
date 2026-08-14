package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.FactureRepository;
import jakarta.mail.internet.MimeMessage;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Identifiant de l'image inline referencee par le template (cid:). */
    private static final String LOGO_CID = "logoSogetherm";

    private final FactureRepository factureRepository;
    private final FacturePdfService facturePdfService;
    private final SpringTemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final SocieteProperties societe;
    private final String expediteur;
    private final DecimalFormat montantFormat;

    public FactureEmailService(FactureRepository factureRepository,
                               FacturePdfService facturePdfService,
                               SpringTemplateEngine templateEngine,
                               JavaMailSender mailSender,
                               SocieteProperties societe,
                               @Value("${app.mail.expediteur}") String expediteur) {
        this.factureRepository = factureRepository;
        this.facturePdfService = facturePdfService;
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
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
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(expediteur);
            helper.setTo(destinataire);
            helper.setSubject("Facture " + facture.getNumero() + " - " + societe.nom());
            // setText doit preceder addInline / addAttachment.
            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("pdf/logos/sogetherm.png");
            if (logo.exists()) {
                helper.addInline(LOGO_CID, logo, "image/png");
            }
            helper.addAttachment("facture-" + facture.getNumero() + ".pdf",
                    new ByteArrayResource(pdf), "application/pdf");

            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envoi de l'email impossible : verifiez la configuration SMTP");
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
        ctx.setVariable("logoCid", new ClassPathResource("pdf/logos/sogetherm.png").exists() ? LOGO_CID : "");
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

package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.DevisRepository;
import jakarta.mail.internet.MimeMessage;
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
import java.util.UUID;

/**
 * Envoie le devis au client par email : PDF en piece jointe et lien personnel
 * lui permettant d'accepter (avec bon de commande) ou de refuser.
 *
 * L'envoi ne modifie PAS le statut du devis : la reponse du client est une
 * simple trace, la validation reste manuelle dans l'application.
 */
@Service
public class DevisEmailService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Identifiant de l'image inline referencee par le template (cid:). */
    private static final String LOGO_CID = "logoSogetherm";

    private final DevisRepository devisRepository;
    private final DevisPdfService devisPdfService;
    private final SpringTemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final SocieteProperties societe;
    private final String expediteur;
    private final String frontendUrl;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final DecimalFormat montantFormat;

    public DevisEmailService(DevisRepository devisRepository,
                             DevisPdfService devisPdfService,
                             SpringTemplateEngine templateEngine,
                             JavaMailSender mailSender,
                             SocieteProperties societe,
                             @Value("${app.mail.expediteur}") String expediteur,
                             @Value("${app.frontend.url}") String frontendUrl,
                             PolitiquePouvoirs politiquePouvoirs) {
        this.devisRepository = devisRepository;
        this.devisPdfService = devisPdfService;
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
        this.societe = societe;
        this.expediteur = expediteur;
        this.frontendUrl = frontendUrl;
        this.politiquePouvoirs = politiquePouvoirs;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    @Transactional
    public void envoyerAuClient(Long devisId) {
        Devis devis = devisRepository.findById(devisId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis", devisId));

        // L'envoi de l'email est independant du statut du devis : on peut le
        // transmettre au client sans l'avoir d'abord marque ENVOYE. Seule
        // exception, la remise : au-dela du seuil, elle doit avoir recu l'aval
        // de l'encadrement avant que le client ne decouvre le prix. Sans quoi
        // un brouillon partirait chez lui sans passer par le controle, qui
        // n'aurait alors plus rien a arbitrer.
        if (politiquePouvoirs.validationAttendue(devis)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de ce devis doit d'abord etre validee par le responsable "
                            + "commercial : il ne peut pas encore etre transmis au client");
        }

        Client client = devis.getClient();
        String destinataire = client.getEmail();
        if (destinataire == null || destinataire.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce client n'a pas d'adresse email : impossible de lui envoyer le devis");
        }

        // Jeton stable : un renvoi du mail reutilise le meme lien.
        if (devis.getTokenClient() == null || devis.getTokenClient().isBlank()) {
            devis.setTokenClient(UUID.randomUUID().toString().replace("-", ""));
        }

        byte[] pdf = devisPdfService.genererDevisPdf(devisId);
        String html = construireCorps(devis, client);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(expediteur);
            helper.setTo(destinataire);
            helper.setSubject("Devis " + devis.getNumero() + " - " + societe.nom());
            // setText doit precéder addInline / addAttachment.
            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("pdf/logos/sogetherm.png");
            if (logo.exists()) {
                helper.addInline(LOGO_CID, logo, "image/png");
            }
            helper.addAttachment("devis-" + devis.getNumero() + ".pdf",
                    new ByteArrayResource(pdf), "application/pdf");

            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envoi de l'email impossible : verifiez la configuration SMTP");
        }

        devis.setDateEnvoiEmail(LocalDateTime.now());
        devisRepository.save(devis);
    }

    private String construireCorps(Devis devis, Client client) {
        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoCid", new ClassPathResource("pdf/logos/sogetherm.png").exists() ? LOGO_CID : "");
        ctx.setVariable("clientNom", nomClient(client));
        ctx.setVariable("numero", devis.getNumero());
        ctx.setVariable("reference", devis.getReference());
        ctx.setVariable("date", devis.getDateCreation() != null
                ? devis.getDateCreation().format(DATE_FR) : "");
        ctx.setVariable("dateValidite", devis.getDateValidite() != null
                ? devis.getDateValidite().format(DATE_FR) : "");
        ctx.setVariable("netAPayer", montantFormat.format(
                devis.getMontantTTC() != null ? devis.getMontantTTC() : BigDecimal.ZERO));
        ctx.setVariable("lienClient", lienClient(devis.getTokenClient()));
        return templateEngine.process("email-devis", ctx);
    }

    /** URL publique du lien personnel remis au client. */
    public String lienClient(String token) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return base + "/devis-client/" + token;
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

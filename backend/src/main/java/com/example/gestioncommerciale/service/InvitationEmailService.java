package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Envoie a l'invite le lien qui lui permet de choisir son mot de passe. */
@Service
public class InvitationEmailService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Identifiant de l'image inline referencee par le template (cid:). */
    private static final String LOGO_CID = "logoSogetherm";

    /** Libelles lisibles : le nom technique du role n'a rien a faire dans un email. */
    private static final Map<Role, String> LIBELLES = Map.of(
            Role.ADMIN, "Administrateur",
            Role.RESPONSABLE_COMMERCIAL, "Responsable commercial",
            Role.COMMERCIAL, "Commercial",
            Role.MAGASINIER, "Magasinier",
            Role.RESPONSABLE_IMPORT, "Responsable import",
            Role.COMPTABLE, "Comptable");

    private final SpringTemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final SocieteProperties societe;
    private final String expediteur;
    private final String frontendUrl;

    public InvitationEmailService(SpringTemplateEngine templateEngine,
                                  JavaMailSender mailSender,
                                  SocieteProperties societe,
                                  @Value("${app.mail.expediteur}") String expediteur,
                                  @Value("${app.frontend.url}") String frontendUrl) {
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
        this.societe = societe;
        this.expediteur = expediteur;
        this.frontendUrl = frontendUrl;
    }

    public void envoyer(Utilisateur invite) {
        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoCid",
                new ClassPathResource("pdf/logos/sogetherm.png").exists() ? LOGO_CID : "");
        ctx.setVariable("nom", invite.getNom());
        ctx.setVariable("prenom", invite.getPrenom());
        ctx.setVariable("email", invite.getEmail());
        ctx.setVariable("roleLibelle",
                LIBELLES.getOrDefault(invite.getRole(), String.valueOf(invite.getRole())));
        ctx.setVariable("lienInvitation", lienInvitation(invite.getTokenInvitation()));
        ctx.setVariable("expiration", invite.getInvitationExpireLe() != null
                ? invite.getInvitationExpireLe().format(DATE_FR) : "");

        String html = templateEngine.process("email-invitation", ctx);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(expediteur);
            helper.setTo(invite.getEmail());
            helper.setSubject("Votre acces a l'application " + societe.nom());
            // setText doit preceder addInline.
            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("pdf/logos/sogetherm.png");
            if (logo.exists()) {
                helper.addInline(LOGO_CID, logo, "image/png");
            }
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envoi de l'invitation impossible : verifiez la configuration SMTP");
        }
    }

    /** URL publique du lien personnel remis a l'invite. */
    public String lienInvitation(String token) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return base + "/invitation/" + token;
    }
}

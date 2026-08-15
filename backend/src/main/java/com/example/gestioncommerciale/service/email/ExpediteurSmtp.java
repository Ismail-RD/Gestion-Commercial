package com.example.gestioncommerciale.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envoi par SMTP : la voie historique, celle qui marche partout ou le port
 * sortant n'est pas filtre.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class ExpediteurSmtp implements ExpediteurEmail {

    /** Identifiant de l'image integree, referencee par les gabarits (cid:). */
    private static final String LOGO_CID = "logoSogetherm";
    private static final String CHEMIN_LOGO = "pdf/logos/sogetherm.png";

    private final JavaMailSender mailSender;
    private final String expediteur;

    public ExpediteurSmtp(JavaMailSender mailSender,
                          @Value("${app.mail.expediteur}") String expediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
    }

    @Override
    public String sourceLogo() {
        return new ClassPathResource(CHEMIN_LOGO).exists() ? "cid:" + LOGO_CID : "";
    }

    @Override
    public void envoyer(MessageEmail message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(expediteur);
            helper.setTo(message.destinataire());
            helper.setSubject(message.sujet());
            // setText doit preceder addInline / addAttachment.
            helper.setText(message.html(), true);

            ClassPathResource logo = new ClassPathResource(CHEMIN_LOGO);
            if (logo.exists()) {
                helper.addInline(LOGO_CID, logo, "image/png");
            }
            for (MessageEmail.PieceJointe piece : message.piecesJointes()) {
                helper.addAttachment(piece.nom(),
                        new ByteArrayResource(piece.contenu()), piece.typeMime());
            }

            mailSender.send(mime);
        } catch (MessagingException e) {
            // Assemblage impossible : ramene au meme type d'erreur que l'envoi,
            // pour que les services metier n'aient qu'un cas a traiter.
            throw new MailSendException("Construction du message impossible", e);
        }
    }
}

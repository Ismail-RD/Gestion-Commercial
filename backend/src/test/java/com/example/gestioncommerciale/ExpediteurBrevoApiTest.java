package com.example.gestioncommerciale;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.service.email.ExpediteurBrevoApi;
import com.example.gestioncommerciale.service.email.MessageEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Envoi par l'API HTTPS de Brevo.
 *
 * <p>Ce chemin existe parce que les hebergeurs filtrent souvent les ports SMTP
 * sortants. Son risque propre est le contrat d'appel : un champ mal nomme et
 * Brevo refuse tout, ce qui ne se verrait qu'en production. D'ou ces tests, qui
 * verifient la forme exacte de la requete sans toucher au reseau.
 */
class ExpediteurBrevoApiTest {

    private static final String URL = "https://api.brevo.test/v3/smtp/email";

    private static final SocieteProperties SOCIETE = new SocieteProperties(
            "SOGETHERM", "Service commercial", "Casablanca", "+212 522 000 000", "",
            "contact@sogetherm.ma", "www.sogetherm.ma", "RC", "PAT", "IF", "CNSS", "ICE");

    private MockRestServiceServer serveur;
    private ExpediteurBrevoApi expediteur;

    @BeforeEach
    void preparer() {
        RestClient.Builder constructeur = RestClient.builder().baseUrl(URL);
        serveur = MockRestServiceServer.bindTo(constructeur).build();
        expediteur = new ExpediteurBrevoApi(constructeur.build(), SOCIETE,
                "xkeysib-test", "contact@sogetherm.ma", "https://app.sogetherm.ma/");
    }

    @Test
    void envoie_le_message_au_format_attendu_par_brevo() {
        byte[] pdf = "contenu-pdf".getBytes(StandardCharsets.UTF_8);

        serveur.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "xkeysib-test"))
                .andExpect(jsonPath("$.sender.email").value("contact@sogetherm.ma"))
                .andExpect(jsonPath("$.sender.name").value("SOGETHERM"))
                .andExpect(jsonPath("$.to[0].email").value("client@exemple.ma"))
                .andExpect(jsonPath("$.subject").value("Devis DEV-2026-001"))
                .andExpect(jsonPath("$.htmlContent").value("<p>Bonjour</p>"))
                .andExpect(jsonPath("$.attachment[0].name").value("devis.pdf"))
                // La piece jointe voyage en base64 : c'est la seule forme que
                // l'API accepte, et une erreur ici passerait inapercue jusqu'a
                // ce qu'un client recoive un fichier illisible.
                .andExpect(jsonPath("$.attachment[0].content")
                        .value(Base64.getEncoder().encodeToString(pdf)))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<202608150000.1@brevo>\"}"));

        expediteur.envoyer(new MessageEmail("client@exemple.ma", "Devis DEV-2026-001",
                "<p>Bonjour</p>", List.of(MessageEmail.PieceJointe.pdf("devis.pdf", pdf))));

        serveur.verify();
    }

    @Test
    void un_message_sans_piece_jointe_n_envoie_pas_de_champ_vide() {
        serveur.expect(requestTo(URL))
                .andExpect(jsonPath("$.attachment").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{}"));

        expediteur.envoyer(new MessageEmail("invite@exemple.ma", "Votre acces", "<p>Bienvenue</p>"));

        serveur.verify();
    }

    @Test
    void un_refus_de_brevo_devient_une_erreur_d_envoi_qui_dit_pourquoi() {
        serveur.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"Sender not valid\"}"));

        MessageEmail message = new MessageEmail("client@exemple.ma", "Sujet", "<p>x</p>");

        MailSendException erreur = assertThrows(MailSendException.class,
                () -> expediteur.envoyer(message));

        // Le service metier ne traite qu'un type d'erreur, et la raison du
        // refus doit survivre jusqu'aux journaux.
        assertTrue(erreur.getMessage().contains("Sender not valid"),
                "la raison du refus doit apparaitre dans le message : " + erreur.getMessage());
    }

    @Test
    void le_logo_est_servi_par_le_frontend_faute_d_image_integree() {
        assertEquals("https://app.sogetherm.ma/logo-sogetherm.png", expediteur.sourceLogo());
    }
}

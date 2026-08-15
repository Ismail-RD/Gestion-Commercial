package com.example.gestioncommerciale.service.email;

import com.example.gestioncommerciale.config.SocieteProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi par l'API HTTPS de Brevo, sur le port 443.
 *
 * <p>Raison d'etre : les hebergeurs mutualises filtrent couramment les ports
 * SMTP sortants (25, 465, 587, 2525) pour empecher l'envoi de courrier
 * indesirable depuis leurs machines. Aucun reglage cote application ne peut
 * lever ce blocage. Le port du web ordinaire, lui, reste toujours ouvert.
 *
 * <p>Une limite a connaitre : contrairement au SMTP, cette API n'accepte pas
 * d'image integree au message. Le logo est donc reference par son adresse
 * publique, et le lecteur devra peut-etre autoriser l'affichage des images
 * distantes. Le texte, lui, s'affiche toujours.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "api")
public class ExpediteurBrevoApi implements ExpediteurEmail {

    private static final Logger log = LoggerFactory.getLogger(ExpediteurBrevoApi.class);

    private final RestClient client;
    private final String cle;
    private final String expediteur;
    private final String nomExpediteur;
    private final String urlLogo;

    public ExpediteurBrevoApi(RestClient clientBrevo,
                              SocieteProperties societe,
                              @Value("${app.mail.brevo.cle:}") String cle,
                              @Value("${app.mail.expediteur}") String expediteur,
                              @Value("${app.frontend.url}") String frontendUrl) {
        this.client = clientBrevo;
        this.cle = cle;
        this.expediteur = expediteur;
        this.nomExpediteur = societe.nom() != null && !societe.nom().isBlank()
                ? societe.nom() : "Service commercial";

        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        this.urlLogo = base + "/logo-sogetherm.png";
    }

    /**
     * Une cle absente ne se verrait qu'au premier envoi, c'est-a-dire devant un
     * utilisateur. Autant refuser de demarrer.
     */
    @PostConstruct
    void verifierLaCle() {
        if (cle.isBlank()) {
            throw new IllegalStateException("""

                    ====================================================================
                    app.mail.transport vaut « api » mais aucune cle Brevo n'est fournie.

                    Renseignez la variable BREVO_API_KEY avec une cle d'API v3
                    (elle commence par xkeysib-), disponible dans Brevo sous
                    « SMTP & API », onglet « API Keys ».

                    Attention : la cle d'API n'est pas la cle SMTP (xsmtpsib-),
                    qui ne sert qu'a l'autre mode d'envoi.
                    ====================================================================
                    """);
        }
    }

    @Override
    public String sourceLogo() {
        return urlLogo;
    }

    @Override
    public void envoyer(MessageEmail message) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("sender", Map.of("email", expediteur, "name", nomExpediteur));
        corps.put("to", List.of(Map.of("email", message.destinataire())));
        corps.put("subject", message.sujet());
        corps.put("htmlContent", message.html());
        if (!message.piecesJointes().isEmpty()) {
            corps.put("attachment", message.piecesJointes().stream()
                    .map(piece -> Map.of(
                            "name", piece.nom(),
                            "content", Base64.getEncoder().encodeToString(piece.contenu())))
                    .toList());
        }

        try {
            client.post()
                    .header("api-key", cle)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (requete, reponse) -> {
                        // Le corps de la reponse porte la raison du refus :
                        // expediteur non valide, compte suspendu, cle revoquee.
                        throw new MailSendException("Brevo a refuse l'envoi ("
                                + reponse.getStatusCode() + ") : " + lire(reponse.getBody()));
                    })
                    .toBodilessEntity();
            log.info("Email remis a Brevo pour {}", message.destinataire());
        } catch (RestClientException e) {
            throw new MailSendException("Appel a l'API Brevo impossible : " + e.getMessage(), e);
        }
    }

    private static String lire(java.io.InputStream flux) {
        try {
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "reponse illisible";
        }
    }
}

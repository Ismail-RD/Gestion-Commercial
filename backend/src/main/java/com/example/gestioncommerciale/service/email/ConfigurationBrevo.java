package com.example.gestioncommerciale.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Le client HTTP qui parle a Brevo.
 *
 * <p>Il est construit ici, et non dans l'expediteur, pour que celui-ci recoive
 * un client tout fait : un test peut alors lui en donner un qui repond sans
 * reseau, et verifier le contenu exact de l'appel.
 */
@Configuration
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "api")
public class ConfigurationBrevo {

    @Bean
    RestClient clientBrevo(RestClient.Builder constructeur,
                           @Value("${app.mail.brevo.url:https://api.brevo.com/v3/smtp/email}") String url) {
        // Sans ces limites, une panne du service distant ferait attendre
        // l'utilisateur jusqu'au delai du systeme.
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(10));
        fabrique.setReadTimeout(Duration.ofSeconds(20));

        return constructeur.baseUrl(url).requestFactory(fabrique).build();
    }
}

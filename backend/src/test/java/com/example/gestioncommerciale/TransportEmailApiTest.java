package com.example.gestioncommerciale;

import com.example.gestioncommerciale.service.email.ExpediteurBrevoApi;
import com.example.gestioncommerciale.service.email.ExpediteurEmail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Demarrage de l'application avec l'envoi d'emails en mode API.
 *
 * <p>Les autres tests tournent en mode SMTP, qui est le defaut : le cablage du
 * mode API n'etait donc eprouve nulle part, et une dependance manquante ne se
 * voyait qu'au demarrage en production. Ce test monte le contexte complet dans
 * ce mode, ce qui suffit a le prouver.
 */
@SpringBootTest(properties = {
        "app.mail.transport=api",
        "app.mail.brevo.cle=xkeysib-test-contexte"
})
class TransportEmailApiTest {

    @Autowired
    private ApplicationContext contexte;

    @Test
    void le_contexte_demarre_et_choisit_l_expedition_par_api() {
        assertInstanceOf(ExpediteurBrevoApi.class, contexte.getBean(ExpediteurEmail.class));
    }
}

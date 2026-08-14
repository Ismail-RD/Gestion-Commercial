package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seuil de remise : un devis envoye par un commercial avec une remise de ligne
 * superieure a 20 % part en attente de validation ; un admin le valide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DevisRemiseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token(String email, String motDePasse) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "motDePasse", motDePasse))))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long postId(String token, String url, Object payload) throws Exception {
        MvcResult res = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long devisAvecRemise(String token, long clientId, long produitId, double remise, String suffixe)
            throws Exception {
        return postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", remise))));
    }

    @Test
    void remise_excessive_d_un_commercial_passe_en_attente_puis_validee_par_admin() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise", "email", "remise@test.local", "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-1", "designation", "Produit remise",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        // Remise 30 % > seuil 20 % : le devis part en attente des la saisie, sans
        // attendre un geste du commercial.
        long devisId = devisAvecRemise(commercial, clientId, produitId, 30.0, "A");
        mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"));

        // L'envoi lui-meme est ferme : il n'y a rien a envoyer avant l'arbitrage
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());

        // Tant qu'il n'est pas valide, il ne peut pas etre accepte (exige ENVOYE)
        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());

        // L'aval de l'admin ne fait pas avancer le devis : il rouvre l'envoi,
        // que le commercial declenche lui-meme quand il le decide.
        mockMvc.perform(post("/api/devis/" + devisId + "/valider-remise")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.remiseAValider").value(false));

        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"));
    }

    @Test
    void remise_dans_le_seuil_est_envoyee_directement() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise OK", "email", "remise-ok@test.local", "typeClient", "PARTICULIER"));
        // Le catalogue n'est pas du ressort du commercial
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-2", "designation", "Produit remise ok",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long devisId = devisAvecRemise(commercial, clientId, produitId, 10.0, "B");
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"));
    }

    /**
     * L'envoi de l'email est independant du statut, mais pas de la remise :
     * sinon un brouillon partirait chez le client sans passer par le controle,
     * qui n'aurait alors plus rien a arbitrer.
     */
    @Test
    void une_remise_excessive_bloque_l_email_des_le_brouillon() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise Email", "email", "remise-mail@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-3", "designation", "Produit remise email",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        // Remise excessive : le devis attend l'arbitrage, l'email est ferme
        long devisId = devisAvecRemise(commercial, clientId, produitId, 30.0, "C");
        mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"))
                .andExpect(jsonPath("$.remiseAValider").value(true));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer-email")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());

        // Un refus le renvoie au brouillon, mais il n'en repart pas tel quel :
        // ni email, ni envoi, tant que la remise n'a pas baisse.
        mockMvc.perform(post("/api/devis/" + devisId + "/refuser-remise")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.remiseAValider").value(true));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer-email")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());

        // Le commercial repasse la main a l'encadrement en reprenant sa saisie
        mockMvc.perform(put("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId, "dateValidite", "2030-12-31",
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 30.0))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"));

        // Une fois la remise validee, l'envoi n'est plus retenu par la remise :
        // il echoue en 503 faute de serveur SMTP en test, plus en 409.
        mockMvc.perform(post("/api/devis/" + devisId + "/valider-remise")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remiseAValider").value(false));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer-email")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Meme regle sur les commandes, ou la remise se retouche a tout moment : la
     * commande est gelee tant que l'encadrement n'a pas tranche, et aucun
     * document n'en sort.
     */
    @Test
    void une_remise_excessive_gele_la_commande() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise Commande", "email", "remise-cmd@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-CMD", "designation", "Produit remise commande",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 20))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId,
                                        "quantite", 2, "remise", 30))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"))
                .andReturn();
        long commandeId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asLong();
        long ligneId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("lignes").get(0).get("id").asLong();

        // Aucun document ne sort
        mockMvc.perform(get("/api/commandes/" + commandeId + "/bon-livraison")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/commandes/" + commandeId + "/preparation")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/factures")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "commandeId", commandeId, "dateEcheance", "2030-12-31"))))
                .andExpect(status().isConflict());

        // Le cycle logistique est gele lui aussi
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "EN_PREPARATION"))))
                .andExpect(status().isConflict());

        // Le commercial ne se valide pas lui-meme
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider-remise")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isForbidden());

        // L'aval de l'encadrement libere la commande
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider-remise")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
        mockMvc.perform(get("/api/commandes/" + commandeId + "/bon-livraison")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isOk());
    }

    /** Baisser la remise sous le seuil libere la commande sans passer par l'encadrement. */
    @Test
    void baisser_la_remise_sort_la_commande_de_l_attente() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise Baissee", "email", "remise-baisse@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-BAI", "designation", "Produit remise baissee",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId,
                                        "quantite", 1, "remise", 40))))))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"))
                .andReturn();
        long commandeId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/commandes/" + commandeId + "/lignes")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("produitId", produitId,
                                        "quantite", 1, "remise", 5))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    /** L'encadrement n'est pas soumis au seuil : il est lui-meme le validateur. */
    @Test
    void une_commande_saisie_par_l_encadrement_n_attend_personne() throws Exception {
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Remise Admin", "email", "remise-admin@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-ADM", "designation", "Produit remise admin",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId,
                                        "quantite", 1, "remise", 50))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    /** Sous le seuil, un brouillon part chez le client sans rien demander. */
    @Test
    void une_remise_dans_le_seuil_n_empeche_pas_l_email_d_un_brouillon() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise Douce", "email", "remise-douce@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "REM-4", "designation", "Produit remise douce",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long devisId = devisAvecRemise(commercial, clientId, produitId, 10.0, "D");
        mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.remiseAValider").value(false));
        // 503 = bloque par l'absence de SMTP en test, pas par la remise
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer-email")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isServiceUnavailable());
    }
}

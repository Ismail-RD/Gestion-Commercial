package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seuils de remise par role : l'administrateur les regle depuis l'application,
 * et le seuil d'un valideur borne aussi ce qu'il peut approuver chez autrui.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeuilsRemiseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Le contexte Spring est partage par toute la suite : on rend aux seuils
     * leurs valeurs d'origine pour ne pas depayser les autres tests.
     */
    @AfterEach
    void retablirLesSeuils() throws Exception {
        regler("COMMERCIAL", 20);
        reglerResponsable(50, 100000);
    }

    private String token(String email, String motDePasse) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "motDePasse", motDePasse))))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String admin() throws Exception {
        return token("admin@gestioncommerciale.local", "Admin@123");
    }

    /** Cree un compte du role voulu et renvoie son jeton. */
    private String compte(String role, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", role, "prenom", "Seuil", "email", email,
                                "motDePasse", "MotDePasse1", "role", role))))
                .andExpect(status().isOk());
        return token(email, "MotDePasse1");
    }

    private void regler(String role, double pourcentage) throws Exception {
        mockMvc.perform(put("/api/parametres/pouvoirs/" + role)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("seuilRemisePct", pourcentage))))
                .andExpect(status().isOk());
    }

    /** Le responsable porte aussi un plafond de credit : on le repose avec. */
    private void reglerResponsable(double pourcentage, double plafondCredit) throws Exception {
        mockMvc.perform(put("/api/parametres/pouvoirs/RESPONSABLE_COMMERCIAL")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "seuilRemisePct", pourcentage,
                                "plafondCreditMax", plafondCredit))))
                .andExpect(status().isOk());
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

    private long produit(String reference) throws Exception {
        return postId(admin(), "/api/produits", Map.of(
                "reference", reference, "designation", "Produit " + reference,
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
    }

    private long devis(String token, long clientId, long produitId, double remise) throws Exception {
        return postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", remise))));
    }

    private String statut(String token, long devisId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("statut").asText();
    }

    @Test
    void seul_l_administrateur_regle_les_seuils() throws Exception {
        String commercial = compte("COMMERCIAL", "seuil-com@test.local");
        String responsable = compte("RESPONSABLE_COMMERCIAL", "seuil-resp@test.local");

        // Tout le monde consulte : chacun doit savoir jusqu'ou il engage l'entreprise
        mockMvc.perform(get("/api/parametres/pouvoirs")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Mais ni le commercial ni son responsable ne les modifient
        for (String jeton : List.of(commercial, responsable)) {
            mockMvc.perform(put("/api/parametres/pouvoirs/COMMERCIAL")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("seuilRemisePct", 90))))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(put("/api/parametres/pouvoirs/COMMERCIAL")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seuilRemisePct", 35))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilRemisePct").value(35));

        // L'ADMIN n'a pas de seuil : il n'y a rien a lui regler
        mockMvc.perform(put("/api/parametres/pouvoirs/ADMIN")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seuilRemisePct", 10))))
                .andExpect(status().isBadRequest());
    }

    /** Le seuil regle depuis l'ecran s'applique a la saisie suivante. */
    @Test
    void le_seuil_du_commercial_prend_effet_immediatement() throws Exception {
        String commercial = compte("COMMERCIAL", "seuil-effet@test.local");
        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Seuil", "email", "seuil-effet-cli@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = produit("SEU-1");

        // A 20 %, une remise de 15 % passe sans arbitrage
        long tolere = devis(commercial, clientId, produitId, 15.0);
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", statut(commercial, tolere));

        // L'administrateur resserre a 10 % : la meme remise demande desormais l'aval
        regler("COMMERCIAL", 10);
        long arbitre = devis(commercial, clientId, produitId, 15.0);
        org.junit.jupiter.api.Assertions.assertEquals(
                "EN_ATTENTE_VALIDATION", statut(commercial, arbitre));
    }

    /**
     * Le seuil du responsable commercial borne ce qu'il valide : au-dela, la
     * remise remonte a l'administrateur, seul a n'avoir aucun plafond.
     */
    @Test
    void le_responsable_ne_valide_pas_au_dela_de_son_propre_seuil() throws Exception {
        String commercial = compte("COMMERCIAL", "seuil-pouvoir-com@test.local");
        String responsable = compte("RESPONSABLE_COMMERCIAL", "seuil-pouvoir-resp@test.local");
        regler("RESPONSABLE_COMMERCIAL", 30);

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Pouvoir", "email", "seuil-pouvoir-cli@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = produit("SEU-2");

        // 25 % : dans les cordes du responsable, il tranche
        long modere = devis(commercial, clientId, produitId, 25.0);
        mockMvc.perform(post("/api/devis/" + modere + "/valider-remise")
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remiseAValider").value(false));

        // 45 % : au-dela de son seuil, il ne couvre pas
        long excessif = devis(commercial, clientId, produitId, 45.0);
        mockMvc.perform(post("/api/devis/" + excessif + "/valider-remise")
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isForbidden());

        // L'administrateur, lui, n'a pas de plafond
        mockMvc.perform(post("/api/devis/" + excessif + "/valider-remise")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remiseAValider").value(false));
    }

    /** Meme regle sur les commandes, ou la remise se retouche a tout moment. */
    @Test
    void le_responsable_ne_valide_pas_au_dela_de_son_seuil_sur_une_commande() throws Exception {
        String commercial = compte("COMMERCIAL", "seuil-cmd-com@test.local");
        String responsable = compte("RESPONSABLE_COMMERCIAL", "seuil-cmd-resp@test.local");
        regler("RESPONSABLE_COMMERCIAL", 30);

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Pouvoir Cmd", "email", "seuil-cmd-cli@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = produit("SEU-3");

        long commandeId = postId(commercial, "/api/commandes", Map.of(
                "clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1, "remise", 45))));

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider-remise")
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider-remise")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    /**
     * Le responsable commercial n'est plus au-dessus du seuil : sa propre saisie
     * remonte a l'administrateur des qu'elle depasse ce qu'il accorde.
     */
    @Test
    void la_saisie_du_responsable_passe_aussi_par_son_seuil() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "seuil-saisie-resp@test.local");
        regler("RESPONSABLE_COMMERCIAL", 30);

        long clientId = postId(responsable, "/api/clients", Map.of(
                "nom", "Client Saisie Resp", "email", "seuil-saisie-cli@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = produit("SEU-4");

        long dansSonSeuil = devis(responsable, clientId, produitId, 28.0);
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", statut(responsable, dansSonSeuil));

        long horsSeuil = devis(responsable, clientId, produitId, 40.0);
        org.junit.jupiter.api.Assertions.assertEquals(
                "EN_ATTENTE_VALIDATION", statut(responsable, horsSeuil));
    }
}

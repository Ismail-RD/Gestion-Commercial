package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Protege contre la regression qui a laisse des devis orphelins :
 * un client rattache a des documents commerciaux ne doit pas pouvoir etre supprime.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientIntegriteTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token() throws Exception {
        return tokenDe("admin@gestioncommerciale.local", "Admin@123");
    }

    private String tokenDe(String email, String motDePasse) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "motDePasse", motDePasse))))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long creer(String token, String url, Object payload) throws Exception {
        MvcResult res = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    void un_client_avec_devis_ne_peut_pas_etre_supprime() throws Exception {
        String token = token();

        long clientId = creer(token, "/api/clients", Map.of(
                "nom", "Client Integrite",
                "email", "integrite@test.local",
                "typeClient", "ENTREPRISE",
                "raisonSociale", "Integrite SARL"));

        long produitId = creer(token, "/api/produits", Map.of(
                "reference", "INT-001",
                "designation", "Produit integrite",
                "prixUnitaireHT", 100.0,
                "tauxTVA", 20));

        creer(token, "/api/devis", Map.of(
                "clientId", clientId,
                "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));

        // La suppression doit etre refusee proprement, pas planter en 500
        mockMvc.perform(delete("/api/clients/" + clientId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("ne peut pas etre supprime")));
    }

    @Test
    void le_commercial_est_celui_qui_saisit_le_client() throws Exception {
        String token = token(); // connecte en tant qu'admin

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Client Auto",
                                "email", "auto@test.local",
                                "typeClient", "PARTICULIER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commercialNom").value("Systeme Admin"));
    }

    @Test
    void un_commercialId_envoye_dans_la_requete_est_ignore() throws Exception {
        String token = token();

        // Tentative d'attribuer le client a quelqu'un d'autre (id 999) :
        // le champ n'existe plus dans le contrat, il doit rester sans effet.
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Client Usurpe\",\"email\":\"usurpe@test.local\","
                                + "\"typeClient\":\"PARTICULIER\",\"commercialId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commercialNom").value("Systeme Admin"));
    }

    @Test
    void seul_un_admin_peut_reattribuer_un_client() throws Exception {
        String tokenAdmin = token();

        long clientId = creer(tokenAdmin, "/api/clients", Map.of(
                "nom", "Client Reattribution",
                "email", "reattrib@test.local",
                "typeClient", "PARTICULIER"));

        // Un commercial n'a pas le droit de rebattre les cartes
        String tokenCommercial = tokenDe("m.benali@sogetherm.ma", "Commercial@123");
        mockMvc.perform(patch("/api/clients/" + clientId + "/commercial")
                        .header("Authorization", "Bearer " + tokenCommercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialId\":1}"))
                .andExpect(status().isForbidden());

        // L'admin, si : le client passe au commercial designe
        mockMvc.perform(patch("/api/clients/" + clientId + "/commercial")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialNom").value("Mohamed Benali"));
    }

    @Test
    void le_prix_negocie_prime_sur_le_prix_catalogue() throws Exception {
        String token = token();

        long clientId = creer(token, "/api/clients", Map.of(
                "nom", "Client Prix",
                "email", "prix@test.local",
                "typeClient", "PARTICULIER"));

        long produitId = creer(token, "/api/produits", Map.of(
                "reference", "PRIX-001",
                "designation", "Produit prix",
                "prixUnitaireHT", 1000.0,
                "tauxTVA", 20));

        // Prix catalogue 1000, on negocie a 150 pour 2 unites -> 300 HT / 360 TTC
        mockMvc.perform(post("/api/devis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "dateValidite", "2030-12-31",
                                "lignes", List.of(Map.of(
                                        "produitId", produitId,
                                        "quantite", 2,
                                        "prixUnitaire", 150.0,
                                        "tauxTVA", 20))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montantHT").value(300.00))
                .andExpect(jsonPath("$.montantTTC").value(360.00));
    }
}

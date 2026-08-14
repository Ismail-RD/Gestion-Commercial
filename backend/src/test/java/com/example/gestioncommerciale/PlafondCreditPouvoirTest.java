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

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plafond de credit : ce que le responsable commercial accorde seul est borne
 * par un parametre que seul l'administrateur regle.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlafondCreditPouvoirTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Le contexte Spring est partage : on rend au parametre sa valeur d'origine. */
    @AfterEach
    void retablirLePouvoir() throws Exception {
        reglerPouvoir("RESPONSABLE_COMMERCIAL", 50, 100000d);
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

    private String compte(String role, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", role, "prenom", "Plafond", "email", email,
                                "motDePasse", "MotDePasse1", "role", role))))
                .andExpect(status().isOk());
        return token(email, "MotDePasse1");
    }

    private void reglerPouvoir(String role, double seuil, Double plafond) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("seuilRemisePct", seuil);
        corps.put("plafondCreditMax", plafond);
        mockMvc.perform(put("/api/parametres/pouvoirs/" + role)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corps)))
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

    @Test
    void le_responsable_n_accorde_pas_plus_de_credit_que_son_pouvoir() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "plafond-resp@test.local");
        reglerPouvoir("RESPONSABLE_COMMERCIAL", 50, 50000d);

        long clientId = postId(responsable, "/api/clients", Map.of(
                "nom", "Client Plafond", "email", "plafond-cli@test.local",
                "typeClient", "PARTICULIER"));

        // Dans les cordes du responsable
        mockMvc.perform(post("/api/clients/" + clientId + "/plafond")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("plafondCredit", 40000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plafondCredit").value(40000));

        // Au-dela, il ne decide plus seul
        mockMvc.perform(post("/api/clients/" + clientId + "/plafond")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("plafondCredit", 80000))))
                .andExpect(status().isForbidden());

        // L'administrateur n'est borne par rien
        mockMvc.perform(post("/api/clients/" + clientId + "/plafond")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("plafondCredit", 80000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plafondCredit").value(80000));
    }

    /** Le commercial ne fixe pas de plafond : lui en attribuer un n'a pas de sens. */
    @Test
    void le_commercial_n_a_pas_de_plafond_de_credit_a_regler() throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("seuilRemisePct", 20);
        corps.put("plafondCreditMax", 10000);
        mockMvc.perform(put("/api/parametres/pouvoirs/COMMERCIAL")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corps)))
                .andExpect(status().isBadRequest());
    }
}

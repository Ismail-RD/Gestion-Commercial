package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'ICE marocain fait exactement 15 chiffres. Il est optionnel (prospect,
 * dossier en cours) mais doit etre valide des lors qu'il est saisi.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IceValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@gestioncommerciale.local",
                                "motDePasse", "Admin@123"))))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private void creerClientAvecIce(String token, String email, String ice, int statutAttendu)
            throws Exception {
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Client ICE",
                                "email", email,
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "ICE SARL",
                                "ice", ice))))
                .andExpect(status().is(statutAttendu));
    }

    @Test
    void un_ice_trop_court_est_refuse() throws Exception {
        creerClientAvecIce(token(), "ice-court@test.local", "123", 400);
    }

    @Test
    void un_ice_avec_des_lettres_est_refuse() throws Exception {
        creerClientAvecIce(token(), "ice-lettres@test.local", "ABCDEFGHIJKLMNO", 400);
    }

    @Test
    void un_ice_de_15_chiffres_est_accepte() throws Exception {
        String token = token();
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Client ICE OK",
                                "email", "ice-ok@test.local",
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "ICE OK SARL",
                                "ice", "003334445000067"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ice").value("003334445000067"));
    }

    @Test
    void un_ice_vide_est_accepte_et_stocke_en_null() throws Exception {
        String token = token();
        // Champ laisse vide dans le formulaire : l'absence doit devenir NULL,
        // sinon la contrainte de format en base rejetterait la chaine vide.
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Client Sans ICE",
                                "email", "ice-vide@test.local",
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "Sans ICE SARL",
                                "ice", ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ice").doesNotExist());
    }

    @Test
    void un_fournisseur_avec_ice_invalide_est_refuse() throws Exception {
        mockMvc.perform(post("/api/fournisseurs")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Fournisseur ICE",
                                "typeFournisseur", "ENTREPRISE",
                                "raisonSociale", "F ICE SARL",
                                "ice", "99"))))
                .andExpect(status().isBadRequest());
    }
}

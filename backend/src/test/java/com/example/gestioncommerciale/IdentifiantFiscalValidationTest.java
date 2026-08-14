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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identifiant fiscal (remplace la TVA intra) : exactement 8 chiffres, optionnel.
 * Egalement : un tiers peut avoir plusieurs telephones et RIB.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdentifiantFiscalValidationTest {

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

    @Test
    void identifiant_fiscal_valide_avec_plusieurs_telephones_et_ribs() throws Exception {
        String token = token();
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Entreprise IF",
                                "email", "if-ok@test.local",
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "IF SARL",
                                "identifiantFiscal", "12345678",
                                "telephones", List.of("+212 600 000 001", "+212 600 000 002"),
                                "ribs", List.of(
                                        Map.of("rib", "011780000012345678901234", "banque", "Attijariwafa"),
                                        Map.of("rib", "007450000098765432109876", "banque", "BMCE"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifiantFiscal").value("12345678"))
                .andExpect(jsonPath("$.telephones.length()").value(2))
                .andExpect(jsonPath("$.ribs.length()").value(2));
    }

    @Test
    void identifiant_fiscal_de_mauvais_format_est_refuse() throws Exception {
        String token = token();
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Entreprise IF KO",
                                "email", "if-ko@test.local",
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "IF KO SARL",
                                "identifiantFiscal", "1234"))))
                .andExpect(status().isBadRequest());
    }
}

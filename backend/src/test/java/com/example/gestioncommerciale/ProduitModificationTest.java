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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-regression : modifier un produit en conservant ses fournisseurs
 * provoquait un DuplicateKeyException (liaison videe puis recreee avec la
 * meme cle composite dans la meme transaction).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProduitModificationTest {

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
    void modifier_un_produit_en_gardant_son_fournisseur_fonctionne() throws Exception {
        String token = token();

        long fournisseurId = creer(token, "/api/fournisseurs", Map.of(
                "nom", "Fournisseur Modif",
                "typeFournisseur", "ENTREPRISE",
                "raisonSociale", "Fournisseur Modif SARL"));

        long produitId = creer(token, "/api/produits", Map.of(
                "reference", "MODIF-001",
                "designation", "Produit a modifier",
                "prixUnitaireHT", 100.0,
                "tauxTVA", 20,
                "fournisseurs", List.of(Map.of(
                        "fournisseurId", fournisseurId,
                        "referenceFournisseur", "REF-A",
                        "estPrincipal", true))));

        // Meme fournisseur conserve, avec un attribut mis a jour :
        // ce cas provoquait le DuplicateKeyException.
        mockMvc.perform(put("/api/produits/" + produitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", "MODIF-001",
                                "designation", "Produit modifie",
                                "prixUnitaireHT", 120.0,
                                "tauxTVA", 20,
                                "fournisseurs", List.of(Map.of(
                                        "fournisseurId", fournisseurId,
                                        "referenceFournisseur", "REF-B",
                                        "estPrincipal", true))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Produit modifie"))
                .andExpect(jsonPath("$.fournisseurs[0].referenceFournisseur").value("REF-B"));
    }

    @Test
    void changer_le_type_d_un_client_est_refuse() throws Exception {
        String token = token();

        long clientId = creer(token, "/api/clients", Map.of(
                "nom", "Type Fige",
                "email", "typefige@test.local",
                "typeClient", "PARTICULIER",
                "prenom", "Karim"));

        // Avant le garde, ce PUT repondait 200 en ignorant le changement
        // et en ecrasant le prenom.
        mockMvc.perform(put("/api/clients/" + clientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Type Fige",
                                "email", "typefige@test.local",
                                "typeClient", "ENTREPRISE",
                                "raisonSociale", "Fige SARL"))))
                .andExpect(status().isConflict());
    }
}

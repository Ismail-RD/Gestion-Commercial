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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD des depots, reserve a l'administrateur, et refus d'effacer l'historique. */
@SpringBootTest
@AutoConfigureMockMvc
class GestionDepotsTest {

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

    private String admin() throws Exception {
        return token("admin@gestioncommerciale.local", "Admin@123");
    }

    private long creerDepot(String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/depots")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void l_administrateur_cree_modifie_et_supprime_un_depot() throws Exception {
        // Le code est normalise : saisi en minuscules, il est stocke en majuscules
        long id = creerDepot(" tanger ");

        mockMvc.perform(get("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TANGER"));

        mockMvc.perform(put("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "TNG"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TNG"));

        // Jamais servi : il s'efface
        mockMvc.perform(delete("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void un_code_deja_pris_est_refuse() throws Exception {
        long id = creerDepot("RABAT");

        // A la creation
        mockMvc.perform(post("/api/depots")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "rabat"))))
                .andExpect(status().isConflict());

        // Comme a la modification
        mockMvc.perform(put("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "SH"))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNoContent());
    }

    /** Un depot qui a recu du stock garde ses lignes : l'effacer ferait un trou. */
    @Test
    void un_depot_qui_a_servi_ne_s_efface_pas() throws Exception {
        long id = creerDepot("FES");
        MvcResult res = mockMvc.perform(post("/api/produits")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", "DEP-1", "designation", "Produit depot",
                                "prixUnitaireHT", 100.0, "tauxTVA", 20))))
                .andExpect(status().isCreated())
                .andReturn();
        long produitId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "FES", "quantite", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/depots/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void la_lecture_est_ouverte_mais_l_ecriture_reste_a_l_administrateur() throws Exception {
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        // Le magasinier a besoin de la liste pour travailler
        mockMvc.perform(get("/api/depots").header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isOk());

        // Mais il ne dessine pas le reseau de depots
        mockMvc.perform(post("/api/depots")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "PIRATE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/depots/1")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "XX"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/depots/1")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isForbidden());
    }
}

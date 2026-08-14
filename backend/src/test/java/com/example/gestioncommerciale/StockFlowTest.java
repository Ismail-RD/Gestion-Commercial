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

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockFlowTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token() throws Exception {
        Map<String, String> login = Map.of(
                "email", "admin@gestioncommerciale.local",
                "motDePasse", "Admin@123");
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private Long creerProduit(String token, String ref) throws Exception {
        Map<String, Object> produit = Map.of(
                "reference", ref,
                "designation", "Article " + ref,
                "prixUnitaireHT", 10.0,
                "tauxTVA", 20);
        MvcResult res = mockMvc.perform(post("/api/produits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(produit)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void entree_sortie_et_transfert_mettent_a_jour_le_stock() throws Exception {
        String token = token();
        Long produitId = creerProduit(token, "STK-001");

        // Entree de 100 au depot 1
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantite").value(is(100.0), Double.class))
                .andExpect(jsonPath("$.depotCode", is("SH")));

        // Sortie de 30 -> reste 70
        mockMvc.perform(post("/api/stock/sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantite").value(is(70.0), Double.class));

        // Transfert de 20 du depot 1 vers depot 2
        mockMvc.perform(post("/api/stock/transfert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotSource", "SH",
                                "depotDestination", "AB", "quantite", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantite").value(is(50.0), Double.class))   // depot 1 : 70 - 20
                .andExpect(jsonPath("$[1].quantite").value(is(20.0), Double.class));  // depot 2 : 0 + 20
    }

    @Test
    void sortie_superieure_au_stock_est_refusee() throws Exception {
        String token = token();
        Long produitId = creerProduit(token, "STK-002");

        mockMvc.perform(post("/api/stock/sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 5))))
                .andExpect(status().isConflict());
    }

    /**
     * Les niveaux de stock ne listent que ce qui est reellement en stock :
     * une ligne retombee a zero apres une sortie n'a plus a y figurer.
     */
    @Test
    void les_niveaux_de_stock_excluent_les_lignes_a_zero() throws Exception {
        String token = token();
        Long produitId = creerProduit(token, "STK-ZERO");

        // Entree puis sortie de la meme quantite -> la ligne existe mais vaut 0
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "AB", "quantite", 7))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/stock/sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "AB", "quantite", 7))))
                .andExpect(status().isOk());

        // Par defaut : la ligne a zero est masquee
        mockMvc.perform(get("/api/stock?produitId=" + produitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Sur demande explicite : elle reste consultable
        mockMvc.perform(get("/api/stock?inclureVides=true&produitId=" + produitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].quantite").value(0));
    }
}

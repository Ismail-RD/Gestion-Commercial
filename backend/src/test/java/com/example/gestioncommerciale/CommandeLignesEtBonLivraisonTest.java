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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retouche des lignes d'une commande avant l'edition de son bon de livraison,
 * puis generation du BL avec et sans les prix.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandeLignesEtBonLivraisonTest {

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

    private long postId(String token, String url, Object payload) throws Exception {
        MvcResult res = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload == null ? "" : objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void entree(String token, long produitId, int quantite) throws Exception {
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", quantite))))
                .andExpect(status().isOk());
    }

    /** Stock total du produit, tel que vu par l'apercu de stock. */
    private double stockTotal(String token, String reference) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/stock/apercu")
                        .param("recherche", reference)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode contenu = objectMapper.readTree(res.getResponse().getContentAsString()).get("content");
        return contenu.get(0).get("stockTotal").asDouble();
    }

    @Test
    void les_lignes_sont_modifiables_puis_le_bon_de_livraison_est_genere() throws Exception {
        String token = token();

        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client BL", "email", "bl@test.local", "typeClient", "PARTICULIER"));
        long produitA = postId(token, "/api/produits", Map.of(
                "reference", "BL-A", "designation", "Produit A",
                "prixUnitaireHT", 100.0, "tauxTVA", 20, "uniteMesure", "PIECE"));
        long produitB = postId(token, "/api/produits", Map.of(
                "reference", "BL-B", "designation", "Produit B",
                "prixUnitaireHT", 50.0, "tauxTVA", 20, "uniteMesure", "METRE"));
        entree(token, produitA, 100);
        entree(token, produitB, 100);

        // Devis a deux produits -> accepte -> commande
        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(
                        Map.of("produitId", produitA, "quantite", 10, "prixUnitaire", 100.0, "tauxTVA", 20),
                        Map.of("produitId", produitB, "quantite", 4, "prixUnitaire", 50.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        long commandeId = postId(token, "/api/commandes/depuis-devis/" + devisId, null);

        // Tant que la commande n'est pas validee, rien n'est preleve
        assertEquals(100.0, stockTotal(token, "BL-A"));
        assertEquals(100.0, stockTotal(token, "BL-B"));

        // On retire le produit B et on porte A a 12
        mockMvc.perform(put("/api/commandes/" + commandeId + "/lignes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lignes", List.of(
                                Map.of("produitId", produitA, "quantite", 12))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.montantHT").value(1200.00));

        assertEquals(100.0, stockTotal(token, "BL-A"));

        // Un produit hors devis est librement ajoutable, au prix du catalogue
        long produitHorsDevis = postId(token, "/api/produits", Map.of(
                "reference", "BL-C", "designation", "Produit C",
                "prixUnitaireHT", 10.0, "tauxTVA", 20));
        entree(token, produitHorsDevis, 50);
        mockMvc.perform(put("/api/commandes/" + commandeId + "/lignes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lignes", List.of(
                                Map.of("produitId", produitA, "quantite", 12),
                                Map.of("produitId", produitB, "quantite", 2),
                                Map.of("produitId", produitHorsDevis, "quantite", 3))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(3))
                // 12*100 + 2*50 + 3*10 = 1330
                .andExpect(jsonPath("$.montantHT").value(1330.00));

        // Bon de livraison, avec puis sans les prix
        byte[] avecPrix = mockMvc.perform(get("/api/commandes/" + commandeId + "/bon-livraison")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] sansPrix = mockMvc.perform(get("/api/commandes/" + commandeId + "/bon-livraison")
                        .param("avecPrix", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(avecPrix.length > 1000, "PDF avec prix vide");
        // Sans les colonnes de prix ni les totaux, le document est plus leger
        assertTrue(sansPrix.length < avecPrix.length,
                "Le BL sans prix devrait etre plus court que celui avec prix");
    }
}

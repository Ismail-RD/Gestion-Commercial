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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cycle stock/commande : rien n'est engage a la creation, la validation reserve
 * le stock du depot choisi (sans le sortir), la livraison le sort reellement et
 * l'annulation rend ce qui etait engage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandeStockTest {

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

    private void action(String token, String method, String url, Object payload, int attendu) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PATCH" -> patch(url);
            default -> throw new IllegalArgumentException(method);
        };
        builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (payload != null) {
            builder.content(objectMapper.writeValueAsString(payload));
        }
        mockMvc.perform(builder).andExpect(status().is(attendu));
    }

    /** Prepare un client, un produit avec du stock au depot 1, un devis accepte -> commande. */
    private long[] preparerCommande(String token, int stockDepot1, int quantiteLigne, String ref)
            throws Exception {
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Stock", "email", ref + "@test.local", "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", ref, "designation", "Produit " + ref,
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        // Mise en stock au depot 1
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", stockDepot1))))
                .andExpect(status().isOk());

        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", quantiteLigne,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));
        action(token, "POST", "/api/devis/" + devisId + "/envoyer", null, 200);
        action(token, "POST", "/api/devis/" + devisId + "/accepter", null, 200);

        MvcResult res = mockMvc.perform(post("/api/commandes/depuis-devis/" + devisId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();
        return new long[]{commandeId, ligneId, produitId};
    }

    /** Quantite reservee du produit dans un depot, via l'apercu de stock. */
    private double reserveDepot(String token, String reference, String depotCode) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/stock/apercu")
                        .param("recherche", reference)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode apercu = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("content").get(0);
        for (JsonNode d : apercu.get("depots")) {
            if (depotCode.equals(d.get("depotCode").asText())) {
                return d.get("quantiteReservee").asDouble();
            }
        }
        return 0;
    }

    private int stockDepot(String token, long produitId, String depot) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/stock?produitId=" + produitId
                        + "&depotCode=" + depot + "&inclureVides=true")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        JsonNode content = objectMapper.readTree(res.getResponse().getContentAsString()).get("content");
        return content.isEmpty() ? 0 : content.get(0).get("quantite").asInt();
    }

    @Test
    void valider_reserve_le_stock_puis_livrer_le_sort() throws Exception {
        String token = token();
        long[] ids = preparerCommande(token, 100, 30, "CMD-STK-1");
        long commandeId = ids[0], ligneId = ids[1], produitId = ids[2];

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.lignes[0].depotCode").value("SH"));

        // La marchandise est promise mais toujours en depot : 100 physiques, 30 reserves
        org.junit.jupiter.api.Assertions.assertEquals(100, stockDepot(token, produitId, "SH"));
        org.junit.jupiter.api.Assertions.assertEquals(30.0, reserveDepot(token, "CMD-STK-1", "SH"));

        action(token, "PATCH", "/api/commandes/" + commandeId + "/statut",
                Map.of("statut", "LIVREE"), 200);

        // Livree : 100 - 30 = 70, et plus rien de reserve
        org.junit.jupiter.api.Assertions.assertEquals(70, stockDepot(token, produitId, "SH"));
        org.junit.jupiter.api.Assertions.assertEquals(0.0, reserveDepot(token, "CMD-STK-1", "SH"));
    }

    /** Une sortie manuelle ne peut pas entamer ce qui est promis a une commande. */
    @Test
    void une_sortie_manuelle_ne_touche_pas_au_stock_reserve() throws Exception {
        String token = token();
        long[] ids = preparerCommande(token, 50, 40, "CMD-STK-5");
        long commandeId = ids[0], ligneId = ids[1], produitId = ids[2];

        action(token, "POST", "/api/commandes/" + commandeId + "/valider",
                Map.of("lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))), 200);

        // 50 en depot dont 40 reserves : seuls 10 sont sortables
        action(token, "POST", "/api/stock/sortie",
                Map.of("produitId", produitId, "depotCode", "SH", "quantite", 15,
                        "motif", "Sortie manuelle"), 409);
        action(token, "POST", "/api/stock/sortie",
                Map.of("produitId", produitId, "depotCode", "SH", "quantite", 10,
                        "motif", "Sortie manuelle"), 200);
        org.junit.jupiter.api.Assertions.assertEquals(40, stockDepot(token, produitId, "SH"));
    }

    /**
     * Le stock n'est engage qu'a la validation : une commande peut donc naitre
     * au-dela du stock disponible, et c'est la validation qui la refuse.
     */
    @Test
    void le_stock_n_est_engage_qu_a_la_validation() throws Exception {
        String token = token();
        // 10 en stock, commande de 50
        long[] ids = preparerCommande(token, 10, 50, "CMD-STK-2");
        long commandeId = ids[0], ligneId = ids[1], produitId = ids[2];

        // La creation n'a touche a rien
        org.junit.jupiter.api.Assertions.assertEquals(10, stockDepot(token, produitId, "SH"));
        org.junit.jupiter.api.Assertions.assertEquals(0.0, reserveDepot(token, "CMD-STK-2", "SH"));

        // La validation, elle, refuse : 50 demandes pour 10 disponibles
        action(token, "POST", "/api/commandes/" + commandeId + "/valider",
                Map.of("lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))), 409);

        // Transaction annulee : rien de reserve, stock intact
        org.junit.jupiter.api.Assertions.assertEquals(10, stockDepot(token, produitId, "SH"));
        org.junit.jupiter.api.Assertions.assertEquals(0.0, reserveDepot(token, "CMD-STK-2", "SH"));
    }

    @Test
    void annuler_une_commande_validee_libere_la_reservation() throws Exception {
        String token = token();
        long[] ids = preparerCommande(token, 100, 40, "CMD-STK-3");
        long commandeId = ids[0], ligneId = ids[1], produitId = ids[2];

        action(token, "POST", "/api/commandes/" + commandeId + "/valider",
                Map.of("lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))), 200);
        org.junit.jupiter.api.Assertions.assertEquals(40.0, reserveDepot(token, "CMD-STK-3", "SH"));

        action(token, "PATCH", "/api/commandes/" + commandeId + "/statut",
                Map.of("statut", "ANNULEE"), 200);

        // Le stock n'a jamais bouge, la reservation est rendue
        org.junit.jupiter.api.Assertions.assertEquals(100, stockDepot(token, produitId, "SH"));
        org.junit.jupiter.api.Assertions.assertEquals(0.0, reserveDepot(token, "CMD-STK-3", "SH"));
    }

    @Test
    void annuler_une_commande_livree_restitue_le_stock() throws Exception {
        String token = token();
        long[] ids = preparerCommande(token, 100, 40, "CMD-STK-6");
        long commandeId = ids[0], ligneId = ids[1], produitId = ids[2];

        action(token, "POST", "/api/commandes/" + commandeId + "/valider",
                Map.of("lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))), 200);
        action(token, "PATCH", "/api/commandes/" + commandeId + "/statut",
                Map.of("statut", "LIVREE"), 200);
        org.junit.jupiter.api.Assertions.assertEquals(60, stockDepot(token, produitId, "SH"));

        action(token, "PATCH", "/api/commandes/" + commandeId + "/statut",
                Map.of("statut", "ANNULEE"), 200);
        // Retour marchandise : 60 + 40 = 100
        org.junit.jupiter.api.Assertions.assertEquals(100, stockDepot(token, produitId, "SH"));
    }

    @Test
    void on_ne_peut_pas_valider_via_le_changement_de_statut_generique() throws Exception {
        String token = token();
        long[] ids = preparerCommande(token, 100, 10, "CMD-STK-4");
        long commandeId = ids[0];

        // Passer directement EN_ATTENTE -> VALIDEE sans choix de depot doit etre refuse
        action(token, "PATCH", "/api/commandes/" + commandeId + "/statut",
                Map.of("statut", "VALIDEE"), 409);
    }
}

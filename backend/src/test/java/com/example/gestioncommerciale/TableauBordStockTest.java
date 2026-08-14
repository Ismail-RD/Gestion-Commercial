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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tableau de bord du stock : les chiffres doivent decrire la realite du depot. */
@SpringBootTest
@AutoConfigureMockMvc
class TableauBordStockTest {

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

    private long postId(String token, String url, Object payload) throws Exception {
        MvcResult res = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void entree(String token, long produitId, String depot, int quantite) throws Exception {
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", depot,
                                "quantite", quantite))))
                .andExpect(status().isOk());
    }

    private void sortie(String token, long produitId, String depot, int quantite) throws Exception {
        mockMvc.perform(post("/api/stock/sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", depot,
                                "quantite", quantite))))
                .andExpect(status().isOk());
    }

    private JsonNode tableauDeBord(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/tableau-de-bord/stock")
                        .param("jours", "90")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    /** Chaque unite en stock vaut son prix catalogue, et ce qui est reserve n'est plus disponible. */
    @Test
    void la_valeur_du_stock_distingue_le_reserve_du_disponible() throws Exception {
        String token = admin();
        JsonNode avant = tableauDeBord(token);

        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBS-1", "designation", "Produit tableau de bord",
                "prixUnitaireHT", 250.0, "tauxTVA", 20));
        entree(token, produitId, "SH", 10);

        JsonNode apres = tableauDeBord(token);
        // 10 x 250 = 2500 de plus qu'avant
        assertEquals(avant.get("valeur").get("totale").asDouble() + 2500,
                apres.get("valeur").get("totale").asDouble(), 0.01);

        // Une commande validee reserve la marchandise : elle reste en stock,
        // mais quitte le disponible.
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client TBS", "email", "tbs@test.local", "typeClient", "PARTICULIER"));
        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 4))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        mockMvc.perform(post("/api/commandes/" + commande.get("id").asLong() + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", commande.get("lignes").get(0).get("id").asLong(),
                                        "depotCode", "SH"))))))
                .andExpect(status().isOk());

        JsonNode reserve = tableauDeBord(token);
        // 4 x 250 = 1000 promis a la commande, la marchandise reste en stock
        assertEquals(apres.get("valeur").get("reservee").asDouble() + 1000,
                reserve.get("valeur").get("reservee").asDouble(), 0.01);
        assertEquals(
                reserve.get("valeur").get("totale").asDouble()
                        - reserve.get("valeur").get("reservee").asDouble(),
                reserve.get("valeur").get("disponible").asDouble(), 0.01);
    }

    /**
     * Un depot a sec pendant qu'un autre a de quoi servir : c'est la decision la
     * plus immediate qu'offre l'ecran.
     */
    @Test
    void un_transfert_est_propose_quand_un_depot_est_a_sec() throws Exception {
        String token = admin();
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBS-2", "designation", "Produit transferable",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        // AB recoit puis vide tout, SH garde du disponible
        entree(token, produitId, "AB", 3);
        entree(token, produitId, "SH", 12);
        mockMvc.perform(post("/api/stock/sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "AB", "quantite", 3))))
                .andExpect(status().isOk());

        JsonNode transferts = tableauDeBord(token).get("transferts");
        boolean propose = false;
        for (JsonNode t : transferts) {
            if (t.get("produitId").asLong() == produitId) {
                assertEquals("AB", t.get("depotDemandeur").asText());
                assertEquals("SH", t.get("depotFournisseur").asText());
                propose = true;
            }
        }
        assertTrue(propose, "Le transfert SH -> AB doit etre propose : " + transferts);
    }

    /** Marchandise en stock qui n'est jamais sortie : de l'argent qui dort. */
    @Test
    void un_produit_qui_ne_sort_pas_apparait_comme_dormant() throws Exception {
        String token = admin();
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBS-3", "designation", "Produit dormant",
                "prixUnitaireHT", 400.0, "tauxTVA", 20));
        entree(token, produitId, "SH", 6);

        JsonNode dormants = tableauDeBord(token).get("dormants");
        boolean present = false;
        for (JsonNode d : dormants) {
            if (d.get("produitId").asLong() == produitId) {
                assertEquals(2400.0, d.get("valeurImmobilisee").asDouble(), 0.01);
                assertTrue(d.get("joursDepuisDerniereSortie").isNull(),
                        "Aucune sortie connue : la duree doit rester nulle plutot que zero");
                present = true;
            }
        }
        assertTrue(present, "Le produit sans sortie doit figurer parmi les dormants");
    }

    /** Les mouvements de la fenetre sont comptes par nature. */
    @Test
    void les_flux_de_la_periode_sont_ventiles_par_type() throws Exception {
        String token = admin();
        JsonNode avant = tableauDeBord(token).get("flux");

        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBS-4", "designation", "Produit flux",
                "prixUnitaireHT", 50.0, "tauxTVA", 20));
        entree(token, produitId, "SH", 7);

        JsonNode apres = tableauDeBord(token).get("flux");
        assertEquals(avant.get("entrees").asDouble() + 7, apres.get("entrees").asDouble(), 0.01);
        assertEquals(avant.get("nombreMouvements").asInt() + 1,
                apres.get("nombreMouvements").asInt());
    }

    /**
     * Un mouvement enregistre la variation du stock : une sortie est negative.
     * Le classement doit remonter le produit qui bouge le plus, pas le moins.
     */
    @Test
    void le_classement_des_rotations_place_le_plus_gros_sortant_en_tete() throws Exception {
        String token = admin();
        long petit = postId(token, "/api/produits", Map.of(
                "reference", "TBS-5A", "designation", "Produit peu sortant",
                "prixUnitaireHT", 10.0, "tauxTVA", 20));
        long gros = postId(token, "/api/produits", Map.of(
                "reference", "TBS-5B", "designation", "Produit tres sortant",
                "prixUnitaireHT", 10.0, "tauxTVA", 20));
        entree(token, petit, "SH", 100);
        entree(token, gros, "SH", 1000);
        sortie(token, petit, "SH", 5);
        sortie(token, gros, "SH", 900);

        JsonNode rotations = tableauDeBord(token).get("rotations");
        assertEquals(gros, rotations.get(0).get("produitId").asLong(),
                "Le produit le plus sortant doit arriver en tete : " + rotations);
        assertEquals(900, rotations.get(0).get("quantiteSortie").asDouble(), 0.01);

        // Et les volumes s'affichent en positif, pas en variation de stock
        assertTrue(tableauDeBord(token).get("flux").get("sorties").asDouble() > 0,
                "Les sorties doivent etre presentees comme un volume");
    }

    @Test
    void la_fenetre_d_observation_est_bornee() throws Exception {
        mockMvc.perform(get("/api/tableau-de-bord/stock")
                        .param("jours", "3")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tableau-de-bord/stock")
                        .param("jours", "1000")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void le_magasinier_y_a_acces() throws Exception {
        mockMvc.perform(get("/api/tableau-de-bord/stock")
                        .header("Authorization", "Bearer "
                                + token("i.rachid@sogetherm.ma", "Magazinier@123")))
                .andExpect(status().isOk());
    }
}

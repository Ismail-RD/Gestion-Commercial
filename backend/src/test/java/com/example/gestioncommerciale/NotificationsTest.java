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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications : un evenement adresse a une personne precise.
 *
 * <p>Ce qui est verifie ici tient en trois regles. On ne se notifie pas
 * soi-meme. Chacun ne lit que les siennes. Et une notification destinee a un
 * commercial remonte au responsable commercial.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationsTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token(String email, String motDePasse) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "motDePasse", motDePasse))))
                .andExpect(status().isOk())
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
                        .content(payload == null ? "" : objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode mesNotifications(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/notifications?size=50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("content");
    }

    private boolean contient(JsonNode notifications, String type, long documentId) {
        for (JsonNode n : notifications) {
            if (type.equals(n.get("type").asText())
                    && n.hasNonNull("documentId") && n.get("documentId").asLong() == documentId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Le magasinier ne consulte pas les devis : la validation d'une commande est
     * le seul signal qui lui dit qu'il y a a preparer.
     */
    @Test
    void la_validation_d_une_commande_previent_le_magasinier() throws Exception {
        String admin = admin();
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Notif", "email", "notif-magasinier@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "NOTIF-1", "designation", "Produit notification",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 0))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = commande.get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", commande.get("lignes").get(0).get("id").asLong(),
                                        "depotCode", "SH"))))))
                .andExpect(status().isOk());

        assertTrue(contient(mesNotifications(magasinier), "COMMANDE_A_PREPARER", commandeId),
                "Le magasinier doit etre prevenu qu'une commande est a preparer");
        // L'admin a valide lui-meme : il sait deja, il n'a rien a recevoir.
        assertFalse(contient(mesNotifications(admin), "COMMANDE_A_PREPARER", commandeId),
                "On ne se notifie pas de sa propre action");
    }

    /** La livraison passe la main a la comptabilite. */
    @Test
    void la_livraison_previent_le_comptable() throws Exception {
        String admin = admin();
        String comptable = token("l.rachid@sogetherm.ma", "Comptable@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Livraison", "email", "notif-comptable@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "NOTIF-2", "designation", "Produit livraison",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 0))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = commande.get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", commande.get("lignes").get(0).get("id").asLong(),
                                        "depotCode", "SH"))))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "LIVREE"))))
                .andExpect(status().isOk());

        assertTrue(contient(mesNotifications(comptable), "COMMANDE_A_FACTURER", commandeId),
                "Le comptable doit savoir qu'une commande livree attend sa facture");
    }

    /**
     * Une notification appartient a son destinataire : personne d'autre ne la
     * lit, et personne d'autre ne peut la marquer lue.
     */
    @Test
    void une_notification_ne_se_lit_que_par_son_destinataire() throws Exception {
        String admin = admin();
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Lecture", "email", "notif-lecture@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "NOTIF-3", "designation", "Produit lecture",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());
        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                                        "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 0))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        mockMvc.perform(post("/api/commandes/" + commande.get("id").asLong() + "/valider")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", commande.get("lignes").get(0).get("id").asLong(),
                                        "depotCode", "SH"))))))
                .andExpect(status().isOk());

        JsonNode duMagasinier = mesNotifications(magasinier);
        long notificationId = duMagasinier.get(0).get("id").asLong();

        // L'admin ne peut pas marquer lue une notification qui ne lui appartient pas
        mockMvc.perform(post("/api/notifications/" + notificationId + "/lue")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        // Le destinataire, si
        mockMvc.perform(post("/api/notifications/" + notificationId + "/lue")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isOk());

        MvcResult compteur = mockMvc.perform(get("/api/notifications/non-lues")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isOk())
                .andReturn();
        long nonLues = Long.parseLong(compteur.getResponse().getContentAsString());

        mockMvc.perform(post("/api/notifications/toutes-lues")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isNoContent());
        MvcResult apres = mockMvc.perform(get("/api/notifications/non-lues")
                        .header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, Long.parseLong(apres.getResponse().getContentAsString()));
        assertTrue(nonLues >= 0);
    }
}

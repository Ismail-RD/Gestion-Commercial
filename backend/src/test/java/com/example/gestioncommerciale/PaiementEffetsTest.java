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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cycle de vie des effets de commerce. La regle centrale : seul un paiement
 * encaisse solde une facture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaiementEffetsTest {

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
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Facture de 240 DH TTC (2 x 100 HT + 20 % de TVA). */
    private long facture(String token, String suffixe) throws Exception {
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client " + suffixe, "email", "eff-" + suffixe + "@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "EFF-" + suffixe, "designation", "Produit " + suffixe,
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long commandeId = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))));
        return postId(token, "/api/factures",
                Map.of("commandeId", commandeId, "dateEcheance", "2030-12-31"));
    }

    private JsonNode lireFacture(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/factures/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    /**
     * Le coeur du sujet : un cheque recu ne solde rien tant qu'il n'est pas
     * encaisse. Le compter plus tot afficherait "payee" sur une facture qui ne
     * l'est pas.
     */
    @Test
    void un_cheque_ne_solde_la_facture_qu_a_l_encaissement() throws Exception {
        String token = token();
        long factureId = facture(token, "CHQ");

        long paiementId = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "CHEQUE",
                "numeroEffet", "1234567", "banqueEmettrice", "Attijariwafa",
                "dateEcheance", LocalDate.now().plusMonths(1).toString()));

        // Recu : rien n'est encaisse, la facture reste due
        JsonNode apresReception = lireFacture(token, factureId);
        assertEquals(0.0, apresReception.get("montantPaye").asDouble(), 0.01);
        assertEquals("EMISE", apresReception.get("statut").asText());

        mockMvc.perform(post("/api/paiements/" + paiementId + "/deposer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DEPOSE"))
                .andExpect(jsonPath("$.dateRemise").exists());

        // Remis en banque : toujours rien encaisse
        assertEquals(0.0, lireFacture(token, factureId).get("montantPaye").asDouble(), 0.01);

        mockMvc.perform(post("/api/paiements/" + paiementId + "/encaisser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENCAISSE"));

        JsonNode soldee = lireFacture(token, factureId);
        assertEquals(240.0, soldee.get("montantPaye").asDouble(), 0.01);
        assertEquals("PAYEE", soldee.get("statut").asText());
    }

    /** Un rejet fait retomber le montant : la facture redevient due. */
    @Test
    void un_effet_rejete_fait_retomber_la_facture() throws Exception {
        String token = token();
        long factureId = facture(token, "REJ");

        long paiementId = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "CHEQUE",
                "numeroEffet", "7654321"));
        mockMvc.perform(post("/api/paiements/" + paiementId + "/encaisser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals("PAYEE", lireFacture(token, factureId).get("statut").asText());

        mockMvc.perform(post("/api/paiements/" + paiementId + "/rejeter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("motif", "Provision insuffisante"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETE"))
                .andExpect(jsonPath("$.motifRejet").value("Provision insuffisante"));

        JsonNode apresRejet = lireFacture(token, factureId);
        assertEquals(0.0, apresRejet.get("montantPaye").asDouble(), 0.01);
        assertEquals("EMISE", apresRejet.get("statut").asText());

        // Un motif vide ne dit pas pourquoi rappeler le client
        long autre = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 100.0, "modePaiement", "CHEQUE"));
        mockMvc.perform(post("/api/paiements/" + autre + "/rejeter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", ""))))
                .andExpect(status().isBadRequest());
    }

    /** Especes, carte et virement sont immediats : rien a encaisser ensuite. */
    @Test
    void les_modes_immediats_soldent_des_l_enregistrement() throws Exception {
        String token = token();
        long factureId = facture(token, "IMM");

        long paiementId = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "ESPECES"));

        JsonNode facture = lireFacture(token, factureId);
        assertEquals(240.0, facture.get("montantPaye").asDouble(), 0.01);
        assertEquals("PAYEE", facture.get("statut").asText());

        // Et le cycle des effets ne s'y applique pas
        mockMvc.perform(post("/api/paiements/" + paiementId + "/deposer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /**
     * Deux effets couvrant chacun la totalite ne doivent pas passer : le second
     * ne serait refuse qu'a l'encaissement, trop tard.
     */
    @Test
    void les_effets_en_attente_comptent_dans_le_reste_a_payer() throws Exception {
        String token = token();
        long factureId = facture(token, "DBL");

        postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "TRAITE",
                "numeroEffet", "TR-1"));

        mockMvc.perform(post("/api/paiements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "factureId", factureId, "montant", 240.0,
                                "modePaiement", "CHEQUE", "numeroEffet", "CH-2"))))
                .andExpect(status().isConflict());
    }

    /**
     * Un effet rejete ne pese rien mais garde sa ligne : effacer la facture
     * laisserait une reference orpheline. Il faut donc pouvoir le retirer.
     */
    @Test
    void une_facture_portant_un_effet_rejete_ne_s_efface_qu_apres_lui() throws Exception {
        String token = token();
        long factureId = facture(token, "SUP");

        long effet = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "CHEQUE",
                "numeroEffet", "CH-SUP"));
        mockMvc.perform(post("/api/paiements/" + effet + "/rejeter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Sans provision"))))
                .andExpect(status().isOk());

        // Le montant est retombe a zero, mais la ligne existe toujours
        assertEquals(0.0, lireFacture(token, factureId).get("montantPaye").asDouble(), 0.01);
        mockMvc.perform(delete("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // Une fois l'effet retire, la facture s'efface
        mockMvc.perform(delete("/api/paiements/" + effet)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    /** Un encaissement ne s'efface pas : il faut le rejeter d'abord. */
    @Test
    void un_paiement_encaisse_ne_se_supprime_pas() throws Exception {
        String token = token();
        long factureId = facture(token, "ENC");

        long paiementId = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "ESPECES"));

        mockMvc.perform(delete("/api/paiements/" + paiementId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // Et la facture reglee ne s'efface pas davantage
        mockMvc.perform(delete("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /** Le portefeuille montre ce qui n'est pas encore encaisse, echeance en tete. */
    @Test
    void le_portefeuille_liste_les_effets_en_attente() throws Exception {
        String token = token();
        long factureId = facture(token, "PTF");

        long effet = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 100.0, "modePaiement", "TRAITE",
                "numeroEffet", "TR-PTF",
                "dateEcheance", LocalDate.now().plusDays(20).toString()));

        MvcResult res = mockMvc.perform(get("/api/paiements/effets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode effets = objectMapper.readTree(res.getResponse().getContentAsString());
        boolean present = false;
        for (JsonNode e : effets) {
            if (e.get("id").asLong() == effet) {
                assertEquals("RECU", e.get("statut").asText());
                assertTrue(e.get("estUnEffet").asBoolean());
                present = true;
            }
        }
        assertTrue(present, "L'effet en attente doit figurer au portefeuille");

        // Une fois encaisse, il quitte le portefeuille
        mockMvc.perform(post("/api/paiements/" + effet + "/encaisser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult apres = mockMvc.perform(get("/api/paiements/effets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode e : objectMapper.readTree(apres.getResponse().getContentAsString())) {
            assertTrue(e.get("id").asLong() != effet,
                    "Un effet encaisse quitte le portefeuille");
        }
    }

    /**
     * Un cheque encaisse sur une facture echue la sort du retard, et son rejet
     * l'y remet : le statut suit le montant reellement recu.
     */
    @Test
    void le_retard_suit_les_encaissements_reels() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Retard Effet", "email", "eff-retard@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "EFF-RET", "designation", "Produit retard effet",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long commandeId = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))));
        long factureId = postId(token, "/api/factures", Map.of(
                "commandeId", commandeId,
                "dateEcheance", LocalDate.now().minusDays(5).toString()));

        assertEquals("EN_RETARD", lireFacture(token, factureId).get("statut").asText());

        long effet = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 240.0, "modePaiement", "CHEQUE",
                "numeroEffet", "CH-RET"));
        mockMvc.perform(post("/api/paiements/" + effet + "/encaisser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals("PAYEE", lireFacture(token, factureId).get("statut").asText());

        mockMvc.perform(post("/api/paiements/" + effet + "/rejeter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Sans provision"))))
                .andExpect(status().isOk());
        assertEquals("EN_RETARD", lireFacture(token, factureId).get("statut").asText(),
                "La facture echue redevient en retard quand le cheque revient impaye");
    }
}

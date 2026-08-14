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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD complet des documents commerciaux : devis, commande (y compris saisie
 * directe sans devis) et facture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrudDocumentsTest {

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

    @Test
    void crud_complet_devis() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client CRUD Devis", "email", "crud-devis@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "CRUD-D", "designation", "Produit crud devis",
                "prixUnitaireHT", 200.0, "tauxTVA", 20));

        // CREATE
        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "reference", "REF-A", "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                        "prixUnitaire", 200.0, "tauxTVA", 20))));

        // READ
        mockMvc.perform(get("/api/devis/" + devisId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("REF-A"))
                .andExpect(jsonPath("$.montantHT").value(400.00));

        // UPDATE
        mockMvc.perform(put("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId, "reference", "REF-B",
                                "dateValidite", "2031-06-30",
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 5,
                                        "prixUnitaire", 200.0, "tauxTVA", 20))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("REF-B"))
                .andExpect(jsonPath("$.montantHT").value(1000.00));

        // DELETE
        mockMvc.perform(delete("/api/devis/" + devisId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/devis/" + devisId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void crud_complet_commande_saisie_directe() throws Exception {
        String token = token();
        long clientA = postId(token, "/api/clients", Map.of(
                "nom", "Client CRUD Cmd A", "email", "crud-cmd-a@test.local",
                "typeClient", "PARTICULIER"));
        long clientB = postId(token, "/api/clients", Map.of(
                "nom", "Client CRUD Cmd B", "email", "crud-cmd-b@test.local",
                "typeClient", "PARTICULIER"));
        long produit1 = postId(token, "/api/produits", Map.of(
                "reference", "CRUD-C1", "designation", "Produit crud 1",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long produit2 = postId(token, "/api/produits", Map.of(
                "reference", "CRUD-C2", "designation", "Produit crud 2",
                "prixUnitaireHT", 50.0, "tauxTVA", 20));

        // CREATE : sans devis, prix repris du catalogue
        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientA,
                                "lignes", List.of(Map.of("produitId", produit1, "quantite", 3))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.devisId").doesNotExist())
                .andExpect(jsonPath("$.montantHT").value(300.00))
                .andReturn();
        JsonNode cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();

        // READ
        mockMvc.perform(get("/api/commandes/" + commandeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientNom").value("Client CRUD Cmd A"));

        // UPDATE : changement de client et de lignes (commande sans devis, EN_ATTENTE)
        mockMvc.perform(put("/api/commandes/" + commandeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientB,
                                "lignes", List.of(
                                        Map.of("produitId", produit1, "quantite", 1),
                                        Map.of("produitId", produit2, "quantite", 4))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientNom").value("Client CRUD Cmd B"))
                .andExpect(jsonPath("$.lignes.length()").value(2))
                // 1*100 + 4*50 = 300
                .andExpect(jsonPath("$.montantHT").value(300.00));

        // Un meme produit deux fois est refuse
        mockMvc.perform(put("/api/commandes/" + commandeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientB,
                                "lignes", List.of(
                                        Map.of("produitId", produit1, "quantite", 1),
                                        Map.of("produitId", produit1, "quantite", 2))))))
                .andExpect(status().isBadRequest());

        // DELETE
        mockMvc.perform(delete("/api/commandes/" + commandeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/commandes/" + commandeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * Comme sur un devis, le prix, la TVA et la remise se negocient directement
     * sur la commande. Omis, ils reprennent les conditions du catalogue.
     */
    @Test
    void les_conditions_saisies_sur_une_ligne_priment_sur_le_catalogue() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Negoce", "email", "negoce@test.local", "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "NEG-1", "designation", "Produit negocie",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        // Sans conditions : le catalogue s'applique (2 x 100 = 200)
        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(100.00))
                .andExpect(jsonPath("$.montantHT").value(200.00))
                .andReturn();
        long commandeId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Prix negocie a 80 avec 10% de remise : 2 x 80 x 0,9 = 144
        mockMvc.perform(put("/api/commandes/" + commandeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                                        "prixUnitaire", 80.0, "tauxTVA", 10, "remise", 10))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(80.00))
                .andExpect(jsonPath("$.lignes[0].remise").value(10.00))
                .andExpect(jsonPath("$.montantHT").value(144.00))
                // TVA a 10% : 144 + 14,40
                .andExpect(jsonPath("$.montantTTC").value(158.40));

        mockMvc.perform(delete("/api/commandes/" + commandeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    /** Le PDF de la facture se genere, et son envoi email trace la date. */
    @Test
    void generer_le_pdf_d_une_facture() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client PDF Facture", "email", "pdf-facture@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "PDF-F", "designation", "Produit facture PDF",
                "prixUnitaireHT", 250.0, "tauxTVA", 20, "uniteMesure", "piece"));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 4))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());

        long factureId = postId(token, "/api/factures",
                Map.of("commandeId", commandeId, "dateEcheance", "2030-06-30"));

        byte[] pdf = mockMvc.perform(get("/api/factures/" + factureId + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 1000, "PDF de facture vide");

        // Jamais envoyee tant qu'on n'a pas declenche l'email
        mockMvc.perform(get("/api/factures/" + factureId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dateEnvoiEmail").doesNotExist());

        // SMTP non configure en test : l'envoi echoue proprement en 503
        mockMvc.perform(post("/api/factures/" + factureId + "/envoyer-email")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void modifier_et_supprimer_une_facture() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client CRUD Fac", "email", "crud-fac@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "CRUD-F", "designation", "Produit crud facture",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 20))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());

        long factureId = postId(token, "/api/factures",
                Map.of("commandeId", commandeId, "dateEcheance", "2030-01-31"));

        // UPDATE : seule l'echeance se renegocie
        mockMvc.perform(put("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("dateEcheance", "2030-03-31"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateEcheance").value("2030-03-31"))
                .andExpect(jsonPath("$.montantTTC").value(240.00));

        // Un paiement rend la facture non supprimable
        postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 100.0, "modePaiement", "ESPECES"));
        mockMvc.perform(delete("/api/factures/" + factureId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /**
     * La numerotation repart du plus haut numero attribue, pas du nombre de
     * lignes : sinon la suppression d'une piece fait retomber le compteur sur un
     * numero deja pris et l'emission suivante echoue sur l'unicite.
     */
    @Test
    void supprimer_une_facture_ne_reattribue_pas_son_numero() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Numero", "email", "numero@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "NUM-1", "designation", "Produit numero",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long cmdA = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));
        long cmdB = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));
        long cmdC = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));

        long facA = postId(token, "/api/factures",
                Map.of("commandeId", cmdA, "dateEcheance", "2030-01-31"));
        long facB = postId(token, "/api/factures",
                Map.of("commandeId", cmdB, "dateEcheance", "2030-01-31"));

        String numeroB = objectMapper.readTree(mockMvc.perform(
                        get("/api/factures/" + facB).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("numero").asText();

        // On supprime la premiere : le trou reste, il ne se comble pas
        mockMvc.perform(delete("/api/factures/" + facA).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // L'emission suivante passe, et ne reprend pas le numero de la seconde
        mockMvc.perform(post("/api/factures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "commandeId", cmdC, "dateEcheance", "2030-01-31"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.not(numeroB)));
    }

    /**
     * Deux factures pour une meme commande doubleraient l'encours du client et
     * le chiffre d'affaires : le doublon est refuse, et la commande disparait
     * de la liste des facturables.
     */
    @Test
    void une_commande_ne_se_facture_qu_une_fois() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Doublon", "email", "doublon@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "DBL-1", "designation", "Produit doublon",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long commandeId = postId(token, "/api/commandes", Map.of(
                "clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))));

        // Facturable tant qu'elle n'est pas facturee
        mockMvc.perform(get("/api/commandes")
                        .param("nonFacturee", "true").param("size", "200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + commandeId + ")]").exists());

        long factureId = postId(token, "/api/factures",
                Map.of("commandeId", commandeId, "dateEcheance", "2030-01-31"));

        // Le doublon est refuse, en nommant la facture qui bloque
        mockMvc.perform(post("/api/factures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "commandeId", commandeId, "dateEcheance", "2030-02-28"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deja facturee")));

        // Et elle ne figure plus parmi les commandes a facturer
        mockMvc.perform(get("/api/commandes")
                        .param("nonFacturee", "true").param("size", "200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + commandeId + ")]").doesNotExist());

        // Supprimer la facture rend la commande facturable a nouveau
        mockMvc.perform(delete("/api/factures/" + factureId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/factures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "commandeId", commandeId, "dateEcheance", "2030-02-28"))))
                .andExpect(status().isCreated());
    }
}

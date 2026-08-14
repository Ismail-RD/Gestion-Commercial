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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plafond de credit : quand une facture porte l'encours (factures impayees)
 * au-dela du plafond, le client passe a BLOQUE et ne peut plus commander tant
 * qu'un admin ne l'a pas debloque.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreditClientTest {

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

    private long commandeDepuisDevisAcceptee(String token, long clientId, long produitId) throws Exception {
        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 1000.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return devisId;
    }

    /**
     * Il n'existe pas de credit illimite : sans plafond explicite, le plafond
     * vaut 0 et la premiere facture impayee bloque deja le client.
     */
    @Test
    void sans_plafond_defini_la_premiere_facture_bloque_le_client() throws Exception {
        String token = token();

        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Sans Plafond", "email", "sans-plafond@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "CRD-0", "designation", "Produit sans plafond",
                "prixUnitaireHT", 1000.0, "tauxTVA", 20));

        // Aucun plafond n'a ete defini : il vaut 0
        mockMvc.perform(get("/api/clients/" + clientId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plafondCredit").value(0))
                .andExpect(jsonPath("$.statut").value("ACTIF"));

        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 100))))
                .andExpect(status().isOk());

        long devisId = commandeDepuisDevisAcceptee(token, clientId, produitId);
        MvcResult cmdRes = mockMvc.perform(post("/api/commandes/depuis-devis/" + devisId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cmd = objectMapper.readTree(cmdRes.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());
        postId(token, "/api/factures", Map.of("commandeId", commandeId, "dateEcheance", "2030-12-31"));

        // Une seule facture impayee suffit : encours > 0 = plafond
        mockMvc.perform(get("/api/clients/" + clientId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.statut").value("BLOQUE"));
    }

    @Test
    void depassement_du_plafond_bloque_le_client_puis_deblocage_admin() throws Exception {
        String token = token();

        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Plafond", "email", "plafond@test.local",
                "typeClient", "PARTICULIER"));
        // Le plafond se definit apres la creation, via l'endpoint dedie
        mockMvc.perform(post("/api/clients/" + clientId + "/plafond")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plafondCredit", 1000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plafondCredit").value(1000));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "CRD-1", "designation", "Produit credit",
                "prixUnitaireHT", 1000.0, "tauxTVA", 20));

        // Du stock pour permettre les commandes
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 100))))
                .andExpect(status().isOk());

        // Le second devis se prepare tant que le client est encore ACTIF : une
        // fois bloque, plus aucun devis ne se cree pour lui.
        long devis2 = commandeDepuisDevisAcceptee(token, clientId, produitId);

        // 1re commande -> validation -> facture (TTC = 1200 > plafond 1000)
        long devis1 = commandeDepuisDevisAcceptee(token, clientId, produitId);
        MvcResult cmdRes = mockMvc.perform(post("/api/commandes/depuis-devis/" + devis1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cmd = objectMapper.readTree(cmdRes.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());

        postId(token, "/api/factures", Map.of("commandeId", commandeId, "dateEcheance", "2030-12-31"));

        // Le client est desormais bloque
        mockMvc.perform(get("/api/clients/" + clientId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BLOQUE"));

        // Une nouvelle commande est refusee tant que le client est bloque
        mockMvc.perform(post("/api/commandes/depuis-devis/" + devis2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // Deblocage par l'admin
        mockMvc.perform(post("/api/clients/" + clientId + "/debloquer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));

        // La commande passe maintenant
        mockMvc.perform(post("/api/commandes/depuis-devis/" + devis2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    /**
     * Chiffrer pour un client bloque n'aurait pas de suite : le devis ne
     * pourrait pas devenir commande. Le refus intervient donc des la creation,
     * et non au moment de conclure l'affaire.
     */
    @Test
    void un_client_bloque_ne_recoit_plus_de_devis() throws Exception {
        String token = token();

        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Bloque Devis", "email", "bloque-devis@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "CRD-BLOC", "designation", "Produit blocage devis",
                "prixUnitaireHT", 500.0, "tauxTVA", 20));
        long autreClient = postId(token, "/api/clients", Map.of(
                "nom", "Client Actif", "email", "actif-devis@test.local",
                "typeClient", "PARTICULIER"));

        Map<String, Object> devis = Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 500.0, "tauxTVA", 20)));

        // Tant qu'il est actif, le devis passe
        long devisId = postId(token, "/api/devis", devis);

        mockMvc.perform(post("/api/clients/" + clientId + "/bloquer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BLOQUE"));

        mockMvc.perform(post("/api/devis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devis)))
                .andExpect(status().isConflict());

        // Le devis existant reste corrigeable : le blocage n'efface pas le travail deja fait
        mockMvc.perform(put("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devis)))
                .andExpect(status().isOk());

        // Mais on ne bascule pas un devis d'un client actif vers un client bloque
        Map<String, Object> versLeBloque = new HashMap<>(devis);
        long devisAutre = postId(token, "/api/devis", Map.of(
                "clientId", autreClient, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 500.0, "tauxTVA", 20))));
        mockMvc.perform(put("/api/devis/" + devisAutre)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versLeBloque)))
                .andExpect(status().isConflict());

        // Une fois debloque, le devis se cree de nouveau
        mockMvc.perform(post("/api/clients/" + clientId + "/debloquer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        postId(token, "/api/devis", devis);
    }
}

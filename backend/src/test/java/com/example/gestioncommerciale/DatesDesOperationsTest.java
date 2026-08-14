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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tracabilite : chaque etape franchie par un document commercial laisse sa
 * date. Un statut dit ou en est l'affaire, jamais depuis quand ; sans ces dates
 * aucun delai n'est mesurable apres coup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatesDesOperationsTest {

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

    private JsonNode lire(String token, String url) throws Exception {
        MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    /** Vrai si le champ est present et renseigne. */
    private boolean date(JsonNode doc, String champ) {
        return doc.hasNonNull(champ);
    }

    @Test
    void le_devis_date_son_envoi_et_l_arbitrage_de_sa_remise() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Dates", "email", "dates-devis@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "DT-DEVIS", "designation", "Produit dates devis",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));

        JsonNode devis = lire(token, "/api/devis/" + devisId);
        assertNull(devis.get("dateEnvoi").isNull() ? null : "posee",
                "Un devis au brouillon n'a pas encore ete envoye");

        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        devis = lire(token, "/api/devis/" + devisId);
        String premierEnvoi = devis.get("dateEnvoi").asText();
        assertNotNull(premierEnvoi);

        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        devis = lire(token, "/api/devis/" + devisId);
        // La reponse du client se date, et l'envoi garde la sienne.
        assertEquals(premierEnvoi, devis.get("dateEnvoi").asText());
        assertNotNull(devis.get("dateReponseClient").asText());
    }

    @Test
    void la_commande_date_chaque_etape_jusqu_a_la_livraison() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Cycle", "email", "dates-commande@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "DT-CMD", "designation", "Produit dates commande",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes/depuis-devis/" + devisId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = commande.get("id").asLong();
        long ligneId = commande.get("lignes").get(0).get("id").asLong();

        // A la creation, rien n'est encore franchi
        assertEquals(false, date(commande, "dateValidation"));
        assertEquals(false, date(commande, "dateEnPreparation"));
        assertEquals(false, date(commande, "dateLivraison"));

        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());
        commande = lire(token, "/api/commandes/" + commandeId);
        assertEquals(true, date(commande, "dateValidation"));

        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "EN_PREPARATION"))))
                .andExpect(status().isOk());
        commande = lire(token, "/api/commandes/" + commandeId);
        String priseEnCharge = commande.get("dateEnPreparation").asText();
        assertNotNull(priseEnCharge);

        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "LIVREE"))))
                .andExpect(status().isOk());
        commande = lire(token, "/api/commandes/" + commandeId);
        assertEquals(true, date(commande, "dateLivraison"));
        // La mise en preparation garde sa date : c'est d'elle que se mesure le
        // temps passe a l'entrepot.
        assertEquals(priseEnCharge, commande.get("dateEnPreparation").asText());
    }

    @Test
    void la_commande_fournisseur_date_le_transit_la_douane_et_les_livraisons() throws Exception {
        String token = token();
        long fournisseurId = postId(token, "/api/fournisseurs", Map.of(
                "nom", "Fournisseur Dates", "email", "dates-cf@test.local",
                "typeFournisseur", "ENTREPRISE", "raisonSociale", "Fournisseur Dates"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "DT-CF", "designation", "Produit dates import",
                "prixUnitaireHT", 500.0, "tauxTVA", 20));

        long id = postId(token, "/api/commandes-fournisseur", Map.of(
                "fournisseurId", fournisseurId, "depotReceptionCode", "SH",
                "devise", "EUR", "tauxChange", 11.0,
                "lignes", List.of(Map.of("produitId", produitId,
                        "quantiteCommandee", 10, "prixUnitaireDevise", 100))));
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/commandes-fournisseur/" + id + "/statut?statut=EN_TRANSIT")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/commandes-fournisseur/" + id + "/statut?statut=EN_DOUANE")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        JsonNode cf = lire(token, "/api/commandes-fournisseur/" + id);
        assertEquals(true, date(cf, "dateCommande"));
        assertEquals(true, date(cf, "dateTransit"));
        assertEquals(true, date(cf, "dateDouane"));

        long ligneId = cf.get("lignes").get(0).get("id").asLong();
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "quantiteRecue", 6))))))
                .andExpect(status().isOk());

        cf = lire(token, "/api/commandes-fournisseur/" + id);
        // Une livraison partielle date la premiere arrivee, mais le dossier
        // n'est pas clos : il n'a pas encore de date de reception.
        assertEquals(true, date(cf, "datePremiereReception"));
        assertEquals(false, date(cf, "dateReception"));
        String premiere = cf.get("datePremiereReception").asText();

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "quantiteRecue", 4))))))
                .andExpect(status().isOk());

        cf = lire(token, "/api/commandes-fournisseur/" + id);
        assertEquals(true, date(cf, "dateReception"));
        // La seconde livraison n'ecrase pas la premiere.
        assertEquals(premiere, cf.get("datePremiereReception").asText());
    }

    @Test
    void la_facture_date_son_reglement_et_l_oublie_si_le_paiement_est_rejete() throws Exception {
        String token = token();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Reglement", "email", "dates-facture@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "DT-FAC", "designation", "Produit dates facture",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/devis/" + devisId + "/accepter")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        MvcResult res = mockMvc.perform(post("/api/commandes/depuis-devis/" + devisId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode commande = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = commande.get("id").asLong();
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", commande.get("lignes").get(0).get("id").asLong(),
                                        "depotCode", "SH"))))))
                .andExpect(status().isOk());

        long factureId = postId(token, "/api/factures", Map.of(
                "commandeId", commandeId, "dateEcheance", "2030-12-31"));
        assertEquals(false, date(lire(token, "/api/factures/" + factureId), "dateReglement"));

        // Un cheque recu ne solde rien : l'argent n'est pas encore arrive.
        long paiementId = postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 120.0, "modePaiement", "CHEQUE",
                "numeroEffet", "CHQ-DATES-1", "banqueEmettrice", "CIH"));
        assertEquals(false, date(lire(token, "/api/factures/" + factureId), "dateReglement"));

        mockMvc.perform(post("/api/paiements/" + paiementId + "/deposer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/paiements/" + paiementId + "/encaisser")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(true, date(lire(token, "/api/factures/" + factureId), "dateReglement"));

        // Cheque sans provision : la facture redevient due, sa date de reglement
        // ne peut pas survivre au rejet.
        mockMvc.perform(post("/api/paiements/" + paiementId + "/rejeter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("motif", "Provision insuffisante"))))
                .andExpect(status().isOk());
        assertEquals(false, date(lire(token, "/api/factures/" + factureId), "dateReglement"));
    }
}

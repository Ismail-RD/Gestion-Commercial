package com.example.gestioncommerciale;

import com.example.gestioncommerciale.service.AlertesEcheances;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alertes declenchees par le seul passage du temps.
 *
 * <p>Le point verifie n'est pas seulement qu'elles partent, mais qu'elles ne se
 * repetent pas : un balayage qui tourne chaque nuit doit rappeler une facture
 * echue sans en produire une notification par jour.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AlertesEcheancesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertesEcheances alertes;

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

    private long postId(String token, String url, Object payload) throws Exception {
        MvcResult res = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload == null ? "" : objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode notificationsDe(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/notifications?size=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("content");
    }

    private long compter(JsonNode notifications, String type, long documentId) {
        long total = 0;
        for (JsonNode n : notifications) {
            if (type.equals(n.get("type").asText())
                    && n.hasNonNull("documentId") && n.get("documentId").asLong() == documentId) {
                total++;
            }
        }
        return total;
    }

    /**
     * Une facture echue est rappelee a la comptabilite -- une fois, meme si le
     * balayage repasse. Sans cette garantie, une facture en retard depuis un
     * mois noierait la cloche sous trente notifications identiques.
     */
    @Test
    void une_facture_echue_alerte_le_comptable_sans_se_repeter() throws Exception {
        String admin = token("admin@gestioncommerciale.local", "Admin@123");
        String comptable = token("l.rachid@sogetherm.ma", "Comptable@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Echeance", "email", "alerte-echeance@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "ALERTE-1", "designation", "Produit alerte",
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

        // Echeance dans le passe : la facture nait deja en retard.
        long factureId = postId(admin, "/api/factures", Map.of(
                "commandeId", commande.get("id").asLong(),
                "dateEcheance", LocalDate.now().minusDays(20).toString()));

        alertes.balayer();
        assertEquals(1, compter(notificationsDe(comptable), "FACTURE_ECHUE", factureId),
                "Le comptable doit etre alerte de la facture echue");

        // Le balayage repasse : la nuit suivante ne doit rien ajouter.
        alertes.balayer();
        alertes.balayer();
        assertEquals(1, compter(notificationsDe(comptable), "FACTURE_ECHUE", factureId),
                "Une meme facture ne se rappelle qu'une fois par mois");
    }

    /**
     * Un effet dont l'echeance approche et qui dort encore en portefeuille :
     * c'est de la tresorerie immobilisee, et rien d'autre ne le signale.
     */
    @Test
    void un_effet_proche_de_l_echeance_alerte_le_comptable() throws Exception {
        String admin = token("admin@gestioncommerciale.local", "Admin@123");
        String comptable = token("l.rachid@sogetherm.ma", "Comptable@123");

        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Effet", "email", "alerte-effet@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "ALERTE-2", "designation", "Produit effet",
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
        long factureId = postId(admin, "/api/factures", Map.of(
                "commandeId", commande.get("id").asLong(),
                "dateEcheance", LocalDate.now().plusMonths(2).toString()));

        // Cheque recu, echeance demain : il aurait deja du partir en banque.
        postId(admin, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 120.0, "modePaiement", "CHEQUE",
                "numeroEffet", "CHQ-ALERTE", "banqueEmettrice", "CIH",
                "dateEcheance", LocalDate.now().plusDays(1).toString()));

        alertes.balayer();
        assertTrue(compter(notificationsDe(comptable), "EFFET_A_REMETTRE", factureId) >= 1,
                "Un effet a echeance proche doit remonter a la comptabilite");
    }

    /** Un devis qui va expirer se relance tant qu'il est encore valable. */
    @Test
    void un_devis_proche_de_l_expiration_alerte_son_commercial() throws Exception {
        String admin = token("admin@gestioncommerciale.local", "Admin@123");
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");

        // Le devis doit appartenir au commercial : il le cree lui-meme.
        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Expiration", "email", "alerte-expiration@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "ALERTE-3", "designation", "Produit expiration",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        long devisId = postId(commercial, "/api/devis", Map.of(
                "clientId", clientId,
                "dateValidite", LocalDate.now().plusDays(2).toString(),
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + commercial)).andExpect(status().isOk());

        alertes.balayer();
        assertEquals(1, compter(notificationsDe(commercial), "DEVIS_EXPIRE_BIENTOT", devisId),
                "Le commercial doit pouvoir relancer avant expiration");

        // La date de validite ne bougera plus : une seule alerte, jamais deux.
        alertes.balayer();
        assertEquals(1, compter(notificationsDe(commercial), "DEVIS_EXPIRE_BIENTOT", devisId));
    }

    /** Un conteneur qui n'arrive pas : le fournisseur ne previendra pas. */
    @Test
    void un_import_en_retard_alerte_le_responsable_import() throws Exception {
        String admin = token("admin@gestioncommerciale.local", "Admin@123");
        String import_ = token("import@sogetherm.ma", "Import@123");

        long fournisseurId = postId(admin, "/api/fournisseurs", Map.of(
                "nom", "Fournisseur Retard", "email", "alerte-import@test.local",
                "typeFournisseur", "ENTREPRISE", "raisonSociale", "Fournisseur Retard"));
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "ALERTE-4", "designation", "Produit import",
                "prixUnitaireHT", 500.0, "tauxTVA", 20));

        long cfId = postId(admin, "/api/commandes-fournisseur", Map.of(
                "fournisseurId", fournisseurId, "depotReceptionCode", "SH",
                "dateArriveePrevue", LocalDate.now().minusDays(10).toString(),
                "devise", "EUR", "tauxChange", 11.0,
                "lignes", List.of(Map.of("produitId", produitId,
                        "quantiteCommandee", 5, "prixUnitaireDevise", 100))));
        // Un brouillon n'engage rien : c'est l'emission qui rend le retard reel.
        mockMvc.perform(post("/api/commandes-fournisseur/" + cfId + "/emettre")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        alertes.balayer();
        assertEquals(1, compter(notificationsDe(import_), "IMPORT_EN_RETARD", cfId),
                "Le responsable import doit voir passer un dossier en retard");
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commandes fournisseur : du bon de commande a l'entree en stock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommandeFournisseurTest {

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

    private long produit(String reference) throws Exception {
        return postId(admin(), "/api/produits", Map.of(
                "reference", reference, "designation", "Produit " + reference,
                "prixUnitaireHT", 500.0, "tauxTVA", 20));
    }

    private long fournisseur(String nom, String email) throws Exception {
        return postId(admin(), "/api/fournisseurs", Map.of(
                "nom", nom, "email", email, "typeFournisseur", "ENTREPRISE",
                "raisonSociale", nom));
    }

    /** Commande a l'import : devise etrangere, taux fige, arrivee prevue. */
    private Map<String, Object> commandeImport(long fournisseurId, long produitId,
                                               double quantite, double prix) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("fournisseurId", fournisseurId);
        corps.put("depotReceptionCode", "SH");
        corps.put("dateArriveePrevue", "2030-06-30");
        corps.put("devise", "EUR");
        corps.put("tauxChange", 11.0);
        corps.put("incoterm", "CIF");
        corps.put("portArrivee", "Casablanca");
        corps.put("lignes", List.of(Map.of(
                "produitId", produitId, "quantiteCommandee", quantite,
                "prixUnitaireDevise", prix)));
        return corps;
    }

    private JsonNode trouver(String token, long id) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/commandes-fournisseur/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private double stock(String token, long produitId, String depot) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/stock")
                        .param("produitId", String.valueOf(produitId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode contenu = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("content");
        for (JsonNode l : contenu) {
            if (l.get("depotCode").asText().equals(depot)) {
                return l.get("quantite").asDouble();
            }
        }
        return 0;
    }

    @Test
    void le_cycle_va_de_la_commande_a_l_entree_en_stock() throws Exception {
        String token = admin();
        long f = fournisseur("Daikin Import", "cf-daikin@test.local");
        long p = produit("CF-1");

        long id = postId(token, "/api/commandes-fournisseur", commandeImport(f, p, 10, 300));
        JsonNode creee = trouver(token, id);
        assertEquals("BROUILLON", creee.get("statut").asText());
        // 10 x 300 EUR = 3000 EUR, soit 33 000 DH au taux retenu
        assertEquals(3000.0, creee.get("montantDevise").asDouble(), 0.01);
        assertEquals(33000.0, creee.get("montantMAD").asDouble(), 0.01);

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("COMMANDEE"))
                .andExpect(jsonPath("$.dateCommande").exists());

        for (String etape : List.of("EN_TRANSIT", "EN_DOUANE")) {
            mockMvc.perform(patch("/api/commandes-fournisseur/" + id + "/statut")
                            .param("statut", etape)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value(etape));
        }

        double avant = stock(token, p, "SH");
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECEPTIONNEE"))
                .andExpect(jsonPath("$.dateReception").exists());

        // Sans precision, tout ce qui etait commande est repute recu
        assertEquals(avant + 10, stock(token, p, "SH"), 0.01);
        assertEquals(10.0, trouver(token, id).get("lignes").get(0)
                .get("quantiteRecue").asDouble(), 0.01);
    }

    /** L'ecart entre commande et reception se garde : c'est un litige a suivre. */
    @Test
    void une_reception_partielle_n_entre_que_ce_qui_est_arrive() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Partiel", "cf-partiel@test.local");
        long p = produit("CF-2");

        long id = postId(token, "/api/commandes-fournisseur", commandeImport(f, p, 20, 100));
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long ligneId = trouver(token, id).get("lignes").get(0).get("id").asLong();
        double avant = stock(token, p, "SH");

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", ligneId, "quantiteRecue", 17))))))
                .andExpect(status().isOk());

        assertEquals(avant + 17, stock(token, p, "SH"), 0.01);
        JsonNode ligne = trouver(token, id).get("lignes").get(0);
        assertEquals(20.0, ligne.get("quantiteCommandee").asDouble(), 0.01);
        assertEquals(17.0, ligne.get("quantiteRecue").asDouble(), 0.01);
    }

    /**
     * Le reliquat d'une livraison incomplete peut encore arriver : sans cela,
     * la marchandise manquante n'entrerait jamais en stock, meme livree plus
     * tard.
     */
    @Test
    void le_reliquat_d_une_reception_partielle_reste_receptionnable() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Reliquat", "cf-reliquat@test.local");
        long p = produit("CF-RELIQUAT");

        // 12 x 100 EUR au taux 11 = 13 200 DH de marchandise, 1 200 DH de frais :
        // le cout debarque attendu est de 1 200 DH l'unite, quel que soit le
        // decoupage des livraisons.
        Map<String, Object> corps = new HashMap<>(commandeImport(f, p, 12, 100));
        corps.put("droitsDouane", 1200);
        long id = postId(token, "/api/commandes-fournisseur", corps);
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long ligneId = trouver(token, id).get("lignes").get(0).get("id").asLong();
        double avant = stock(token, p, "SH");

        // Premiere livraison : 9 sur 12
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "quantiteRecue", 9))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECEPTIONNEE_PARTIELLEMENT"));
        assertEquals(avant + 9, stock(token, p, "SH"), 0.01);
        // Les frais se repartissent au prorata de ce qui restait a recevoir : la
        // premiere arrivee n'en porte que sa part (900 sur 1 200).
        assertEquals(1200.0, trouver(token, id).get("lignes").get(0)
                .get("coutUnitaireMAD").asDouble(), 0.01);

        // On ne peut pas recevoir plus que le reliquat
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "quantiteRecue", 5))))))
                .andExpect(status().isConflict());

        // Le complement arrive : le dossier se ferme
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "quantiteRecue", 3))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECEPTIONNEE"));

        assertEquals(avant + 12, stock(token, p, "SH"), 0.01);
        JsonNode ligne = trouver(token, id).get("lignes").get(0);
        assertEquals(12.0, ligne.get("quantiteRecue").asDouble(), 0.01);
        // Le solde des frais part avec la derniere livraison : le cout n'a pas bouge.
        assertEquals(1200.0, ligne.get("coutUnitaireMAD").asDouble(), 0.01);
        assertEquals(1200.0, ligne.get("quotePartFrais").asDouble(), 0.01);

        // Et il n'y a plus rien a receptionner
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /** La date de commande se declare : une commande passee la veille garde sa date. */
    @Test
    void la_date_de_commande_peut_etre_declaree() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Date", "cf-date@test.local");
        long p = produit("CF-DATE");

        Map<String, Object> corps = new HashMap<>(commandeImport(f, p, 2, 100));
        corps.put("dateCommande", "2026-07-15");
        corps.put("modeTransport", "MARITIME");
        corps.put("paysOrigine", "Thailande");
        long id = postId(token, "/api/commandes-fournisseur", corps);

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // L'emission ne l'ecrase pas
                .andExpect(jsonPath("$.dateCommande").value("2026-07-15"))
                .andExpect(jsonPath("$.modeTransport").value("MARITIME"))
                .andExpect(jsonPath("$.paysOrigine").value("Thailande"));
    }

    /** Fret et assurance factures en devise sont convertis au taux du dossier. */
    @Test
    void les_frais_de_transport_peuvent_etre_en_devise() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Frais Devise", "cf-fraisdev@test.local");
        long p = produit("CF-FRAISDEV");

        Map<String, Object> corps = new HashMap<>(commandeImport(f, p, 10, 100));
        corps.put("tauxChange", 10.0);
        corps.put("fraisTransportEnDevise", true);
        corps.put("fraisFret", 200);        // 200 EUR = 2 000 MAD
        corps.put("droitsDouane", 3000);    // deja en dirhams
        long id = postId(token, "/api/commandes-fournisseur", corps);

        // 200 x 10 + 3 000 = 5 000 DH de frais
        assertEquals(5000.0, trouver(token, id).get("totalFrais").asDouble(), 0.01);
    }

    /** Recevoir plus que commande signale une erreur de saisie, pas un cadeau. */
    @Test
    void on_ne_receptionne_pas_plus_que_ce_qui_etait_commande() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Exces", "cf-exces@test.local");
        long p = produit("CF-3");

        long id = postId(token, "/api/commandes-fournisseur", commandeImport(f, p, 5, 100));
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long ligneId = trouver(token, id).get("lignes").get(0).get("id").asLong();

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", ligneId, "quantiteRecue", 8))))))
                .andExpect(status().isConflict());
    }

    /** Un bon de commande emis est un engagement : ses lignes sont figees. */
    @Test
    void une_commande_emise_ne_se_retouche_plus() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Fige", "cf-fige@test.local");
        long p = produit("CF-4");

        long id = postId(token, "/api/commandes-fournisseur", commandeImport(f, p, 4, 100));

        // Au brouillon, tout se corrige
        mockMvc.perform(put("/api/commandes-fournisseur/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandeImport(f, p, 6, 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].quantiteCommandee").value(6));

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/commandes-fournisseur/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandeImport(f, p, 9, 100))))
                .andExpect(status().isConflict());

        // Et elle ne s'efface plus : l'engagement doit rester lisible
        mockMvc.perform(delete("/api/commandes-fournisseur/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    /** Un achat local se passe de devise : le dirham au taux 1. */
    @Test
    void un_achat_local_n_a_pas_besoin_de_taux_de_change() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Local", "cf-local@test.local");
        long p = produit("CF-5");

        long id = postId(token, "/api/commandes-fournisseur", Map.of(
                "fournisseurId", f, "depotReceptionCode", "AB",
                "lignes", List.of(Map.of("produitId", p,
                        "quantiteCommandee", 3, "prixUnitaireDevise", 250))));

        JsonNode creee = trouver(token, id);
        assertEquals("MAD", creee.get("devise").asText());
        assertEquals(1.0, creee.get("tauxChange").asDouble(), 0.0001);
        assertEquals(750.0, creee.get("montantMAD").asDouble(), 0.01);
    }

    /** Une devise etrangere sans taux rendrait le cout de revient incalculable. */
    @Test
    void une_devise_etrangere_exige_un_taux() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Sans Taux", "cf-sanstaux@test.local");
        long p = produit("CF-6");

        mockMvc.perform(post("/api/commandes-fournisseur")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fournisseurId", f, "depotReceptionCode", "SH",
                                "devise", "USD",
                                "lignes", List.of(Map.of("produitId", p,
                                        "quantiteCommandee", 2, "prixUnitaireDevise", 100))))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Les frais du dossier se repartissent sur les lignes recues, au prorata de
     * leur valeur, et donnent le cout debarque de chaque unite.
     */
    @Test
    void les_frais_du_dossier_donnent_le_cout_de_revient() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Cout", "cf-cout@test.local");
        long p = produit("CF-COUT");

        // 10 x 100 EUR au taux 10 = 10 000 MAD de marchandise
        Map<String, Object> corps = new HashMap<>(commandeImport(f, p, 10, 100));
        corps.put("tauxChange", 10.0);
        corps.put("fraisFret", 1500);
        corps.put("droitsDouane", 2000);
        corps.put("fraisTransit", 500);
        long id = postId(token, "/api/commandes-fournisseur", corps);

        JsonNode creee = trouver(token, id);
        assertEquals(4000.0, creee.get("totalFrais").asDouble(), 0.01);
        // 10 000 MAD de marchandise + 4 000 de frais
        assertEquals(14000.0, creee.get("coutTotalMAD").asDouble(), 0.01);

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode ligne = trouver(token, id).get("lignes").get(0);
        assertEquals(4000.0, ligne.get("quotePartFrais").asDouble(), 0.01);
        // (10 000 + 4 000) / 10 unites = 1 400 DH l'unite
        assertEquals(1400.0, ligne.get("coutUnitaireMAD").asDouble(), 0.01);

        // Et le produit porte desormais ce cout de revient
        MvcResult res = mockMvc.perform(get("/api/produits/" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1400.0, objectMapper.readTree(res.getResponse().getContentAsString())
                .get("coutRevientMoyen").asDouble(), 0.01);
    }

    /**
     * Les frais ne s'imputent qu'a la marchandise entree en stock : une ligne
     * non livree n'a ni quote-part ni cout de revient. Sa part reste en attente
     * du reliquat, elle ne se reporte pas sur les lignes deja recues.
     */
    @Test
    void les_frais_ne_chargent_que_les_lignes_recues() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Repartition", "cf-repart@test.local");
        long arrive = produit("CF-ARRIVE");
        long manquant = produit("CF-MANQUANT");

        Map<String, Object> corps = new HashMap<>();
        corps.put("fournisseurId", f);
        corps.put("depotReceptionCode", "SH");
        corps.put("devise", "EUR");
        corps.put("tauxChange", 10.0);
        corps.put("droitsDouane", 900);
        corps.put("lignes", List.of(
                Map.of("produitId", arrive, "quantiteCommandee", 10, "prixUnitaireDevise", 100),
                Map.of("produitId", manquant, "quantiteCommandee", 5, "prixUnitaireDevise", 100)));
        long id = postId(token, "/api/commandes-fournisseur", corps);
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode lignes = trouver(token, id).get("lignes");
        long ligneManquante = lignes.get(0).get("produitId").asLong() == manquant
                ? lignes.get(0).get("id").asLong() : lignes.get(1).get("id").asLong();

        // Le second produit n'arrive pas du tout
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of(
                                        "ligneId", ligneManquante, "quantiteRecue", 0))))))
                .andExpect(status().isOk());

        for (JsonNode l : trouver(token, id).get("lignes")) {
            if (l.get("produitId").asLong() == manquant) {
                assertEquals(0.0, l.get("quotePartFrais").asDouble(), 0.01);
                assertTrue(l.get("coutUnitaireMAD").isNull(),
                        "Une ligne non livree n'a pas de cout de revient");
            } else {
                // La ligne arrivee prend sa part au prorata de la valeur attendue
                // (1 000 sur 1 500), soit 600 DH. Les 300 DH restants attendent
                // le reliquat plutot que de gonfler le cout du deja recu.
                assertEquals(600.0, l.get("quotePartFrais").asDouble(), 0.01);
                assertEquals(1060.0, l.get("coutUnitaireMAD").asDouble(), 0.01);
            }
        }
    }

    /** Deux arrivages a des couts differents se melangent au prorata des quantites. */
    @Test
    void le_cout_moyen_se_repondere_a_chaque_reception() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur CUMP", "cf-cump@test.local");
        long p = produit("CF-CUMP");

        // 10 unites a 1 000 DH
        long premier = postId(token, "/api/commandes-fournisseur",
                achat(f, p, 10, 1000));
        receptionner(token, premier);
        assertEquals(1000.0, coutRevient(token, p), 0.01);

        // Puis 10 a 2 000 DH : la moyenne doit s'etablir a 1 500
        long second = postId(token, "/api/commandes-fournisseur",
                achat(f, p, 10, 2000));
        receptionner(token, second);
        assertEquals(1500.0, coutRevient(token, p), 0.01);
    }

    /** Achat local sans frais : le cout est le prix paye. */
    private Map<String, Object> achat(long fournisseurId, long produitId,
                                      double quantite, double prix) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("fournisseurId", fournisseurId);
        corps.put("depotReceptionCode", "SH");
        corps.put("lignes", List.of(Map.of(
                "produitId", produitId, "quantiteCommandee", quantite,
                "prixUnitaireDevise", prix)));
        return corps;
    }

    private void receptionner(String token, long id) throws Exception {
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private double coutRevient(String token, long produitId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/produits/" + produitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("coutRevientMoyen").asDouble();
    }

    /** L'achat est le metier du responsable import, pas de la force de vente. */
    @Test
    void l_achat_est_reserve_au_perimetre_fournisseur() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        for (String jeton : List.of(commercial, magasinier)) {
            mockMvc.perform(get("/api/commandes-fournisseur")
                            .header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/commandes-fournisseur")
                        .header("Authorization", "Bearer "
                                + token("import@sogetherm.ma", "Import@123")))
                .andExpect(status().isOk());
    }

    /** Un fournisseur engage dans une commande garde sa trace. */
    @Test
    void un_fournisseur_avec_commande_ne_s_efface_pas() throws Exception {
        String token = admin();
        long f = fournisseur("Fournisseur Engage", "cf-engage@test.local");
        long p = produit("CF-7");
        postId(token, "/api/commandes-fournisseur", commandeImport(f, p, 2, 100));

        mockMvc.perform(delete("/api/fournisseurs/" + f)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un endpoint, six compositions : chacun voit son metier, et rien de plus. */
@SpringBootTest
@AutoConfigureMockMvc
class TableauBordRolesTest {

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

    private String compte(String role, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", role, "prenom", "Bord", "email", email,
                                "motDePasse", "MotDePasse1", "role", role))))
                .andExpect(status().isOk());
        return token(email, "MotDePasse1");
    }

    private JsonNode tableauDeBord(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/tableau-de-bord")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
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

    private boolean contientFile(JsonNode tableau, String titre) {
        for (JsonNode f : tableau.get("files")) {
            if (f.get("titre").asText().equals(titre)) {
                return true;
            }
        }
        return false;
    }

    /** Retrouve un chiffre par son libelle : l'ordre des tuiles n'est pas un contrat. */
    private String indicateur(JsonNode tableau, String libelle) {
        for (JsonNode i : tableau.get("indicateurs")) {
            if (i.get("libelle").asText().equals(libelle)) {
                return i.get("valeur").asText();
            }
        }
        return null;
    }

    private JsonNode file(JsonNode tableau, String titre) {
        for (JsonNode f : tableau.get("files")) {
            if (f.get("titre").asText().equals(titre)) {
                return f;
            }
        }
        return null;
    }

    @Test
    void chaque_role_recoit_son_propre_tableau() throws Exception {
        assertEquals("ADMIN", tableauDeBord(admin()).get("role").asText());
        assertEquals("MAGASINIER",
                tableauDeBord(token("i.rachid@sogetherm.ma", "Magazinier@123")).get("role").asText());
        assertEquals("COMMERCIAL",
                tableauDeBord(token("m.benali@sogetherm.ma", "Commercial@123")).get("role").asText());
        assertEquals("RESPONSABLE_IMPORT",
                tableauDeBord(token("import@sogetherm.ma", "Import@123")).get("role").asText());

        // Et chacun a bien quatre chiffres en tete
        assertEquals(4, tableauDeBord(admin()).get("indicateurs").size());
    }

    /** Le magasinier voit le cycle des commandes, dans l'ordre du travail. */
    @Test
    void le_magasinier_voit_sa_file_de_travail() throws Exception {
        String token = admin();
        String magasinier = token("i.rachid@sogetherm.ma", "Magazinier@123");

        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBR-1", "designation", "Produit tableau roles",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Bord", "email", "bord@test.local", "typeClient", "PARTICULIER"));
        postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))));

        JsonNode tableau = tableauDeBord(magasinier);
        JsonNode aValider = file(tableau, "Commandes a valider");
        assertTrue(aValider != null && aValider.get("total").asInt() > 0,
                "La commande en attente doit figurer dans la file du magasinier");

        // Ce qui ne le concerne pas n'apparait pas
        assertFalse(contientFile(tableau, "Clients bloques"));
        assertFalse(contientFile(tableau, "Factures en retard"));
    }

    /** Le commercial ne compte que ses propres dossiers. */
    @Test
    void le_commercial_ne_voit_que_son_portefeuille() throws Exception {
        String autre = compte("COMMERCIAL", "bord-autre@test.local");
        long clientAutre = postId(autre, "/api/clients", Map.of(
                "nom", "Client Autre Bord", "email", "bord-autre-cli@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(admin(), "/api/produits", Map.of(
                "reference", "TBR-2", "designation", "Produit portefeuille",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        postId(autre, "/api/devis", Map.of(
                "clientId", clientAutre, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));

        // Un commercial tiers ne compte pas le devis de son collegue
        String tiers = compte("COMMERCIAL", "bord-tiers@test.local");
        assertEquals("0", indicateur(tableauDeBord(tiers), "Mes devis en cours"),
                "Un commercial neuf n'a aucun devis en cours");

        // Alors que l'auteur, si
        assertEquals("1", indicateur(tableauDeBord(autre), "Mes devis en cours"));

        // Et son visuel ne remonte que ses propres clients
        JsonNode visuel = tableauDeBord(tiers).get("visuel");
        assertEquals(0, visuel.get("barres").size(),
                "Un commercial neuf n'a aucun client a classer");
    }

    /** Le responsable import travaille sur les trous du catalogue. */
    @Test
    void le_responsable_import_voit_les_fiches_incompletes() throws Exception {
        postId(admin(), "/api/produits", Map.of(
                "reference", "TBR-3", "designation", "Produit sans rien",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));

        JsonNode tableau = tableauDeBord(token("import@sogetherm.ma", "Import@123"));
        JsonNode sansFiche = file(tableau, "Sans fiche technique");
        assertTrue(sansFiche != null && sansFiche.get("total").asInt() > 0,
                "Un produit sans fiche technique doit remonter");
        assertFalse(contientFile(tableau, "Commandes a valider"),
                "Le catalogue n'a rien a voir avec l'entrepot");
    }

    /** Une file vide n'apparait pas : l'ecran ne montre que ce qui appelle une action. */
    @Test
    void les_files_vides_ne_sont_pas_affichees() throws Exception {
        JsonNode tableau = tableauDeBord(compte("COMPTABLE", "bord-compta@test.local"));
        for (JsonNode f : tableau.get("files")) {
            assertTrue(f.get("total").asInt() > 0,
                    "Aucune file vide ne doit etre renvoyee : " + f.get("titre").asText());
        }
    }

    /**
     * Une facture reglee sans email a ete remise autrement : la reclamer dans
     * une file de travail demanderait une action qui n'a plus lieu d'etre.
     */
    @Test
    void une_facture_payee_ne_reste_pas_a_transmettre() throws Exception {
        String token = admin();
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Solde", "email", "bord-solde@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBR-4", "designation", "Produit solde",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long commandeId = postId(token, "/api/commandes", Map.of("clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))));
        long factureId = postId(token, "/api/factures",
                Map.of("commandeId", commandeId, "dateEcheance", "2030-12-31"));

        String comptable = compte("COMPTABLE", "bord-solde-compta@test.local");
        JsonNode aTransmettre = file(tableauDeBord(comptable), "Jamais transmises au client");
        assertTrue(aTransmettre != null && aTransmettre.get("total").asInt() > 0,
                "Une facture emise et jamais envoyee doit remonter");
        int avant = aTransmettre.get("total").asInt();

        // Reglee sans jamais avoir ete envoyee : elle sort de la file
        postId(token, "/api/paiements", Map.of(
                "factureId", factureId, "montant", 120.0, "modePaiement", "ESPECES"));

        JsonNode apres = file(tableauDeBord(comptable), "Jamais transmises au client");
        int total = apres == null ? 0 : apres.get("total").asInt();
        assertEquals(avant - 1, total,
                "La facture soldee ne doit plus figurer parmi celles a transmettre");
    }

    /** Chaque tableau porte son visuel, jamais vide de barres. */
    @Test
    void chaque_role_recoit_un_visuel() throws Exception {
        for (String jeton : List.of(admin(),
                token("i.rachid@sogetherm.ma", "Magazinier@123"),
                token("m.benali@sogetherm.ma", "Commercial@123"),
                token("import@sogetherm.ma", "Import@123"))) {
            JsonNode visuel = tableauDeBord(jeton).get("visuel");
            assertFalse(visuel.isNull(), "Chaque role doit avoir un visuel");
            assertFalse(visuel.get("titre").asText().isBlank());
        }

        // Le chiffre sur 12 mois compte bien douze points, mois courant inclus
        JsonNode douzeMois = tableauDeBord(admin()).get("visuel");
        assertEquals("Chiffre facture sur 12 mois", douzeMois.get("titre").asText());
        assertEquals(12, douzeMois.get("barres").size());

        // La balance agee range par anciennete du retard
        JsonNode balance = tableauDeBord(compte("COMPTABLE", "bord-visuel@test.local")).get("visuel");
        assertEquals("Balance agee", balance.get("titre").asText());
        assertEquals(5, balance.get("barres").size());
        assertEquals("Non echu", balance.get("barres").get(0).get("libelle").asText());
    }

    /**
     * Le responsable commercial ne voit que ce qu'il peut trancher : lui montrer
     * une remise hors de son seuil l'enverrait se prendre un refus.
     */
    @Test
    void le_responsable_ne_voit_que_les_remises_de_son_ressort() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "bord-seuil-resp@test.local");
        String commercial = compte("COMMERCIAL", "bord-seuil-com@test.local");

        // Son seuil est ramene a 30 % : au-dela, l'arbitrage revient a l'admin
        reglerSeuilResponsable(30);
        try {
            long clientId = postId(commercial, "/api/clients", Map.of(
                    "nom", "Client Seuil Bord", "email", "bord-seuil-cli@test.local",
                    "typeClient", "PARTICULIER"));
            long produitId = postId(admin(), "/api/produits", Map.of(
                    "reference", "TBR-5", "designation", "Produit seuil bord",
                    "prixUnitaireHT", 100.0, "tauxTVA", 20));

            // 25 % : dans ses cordes. 45 % : hors de son seuil.
            postId(commercial, "/api/devis", Map.of("clientId", clientId,
                    "dateValidite", "2030-12-31",
                    "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                            "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 25.0))));
            postId(commercial, "/api/devis", Map.of("clientId", clientId,
                    "dateValidite", "2030-12-31",
                    "lignes", List.of(Map.of("produitId", produitId, "quantite", 1,
                            "prixUnitaire", 100.0, "tauxTVA", 20, "remise", 45.0))));

            int pourLui = file(tableauDeBord(responsable), "Remises a arbitrer").get("total").asInt();
            int pourAdmin = file(tableauDeBord(admin()), "Remises a arbitrer").get("total").asInt();
            assertTrue(pourAdmin > pourLui,
                    "L'admin doit voir au moins la remise hors seuil en plus : "
                            + pourLui + " vs " + pourAdmin);
        } finally {
            // Le contexte Spring est partage : on rend sa valeur d'origine.
            reglerSeuilResponsable(50);
        }
    }

    private void reglerSeuilResponsable(int seuil) throws Exception {
        mockMvc.perform(put("/api/parametres/pouvoirs/RESPONSABLE_COMMERCIAL")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "seuilRemisePct", seuil, "plafondCreditMax", 100000))))
                .andExpect(status().isOk());
    }

    /**
     * Le retard d'arrivee est le signal qui n'arrive jamais tout seul : le
     * fournisseur ne previent pas qu'il a du retard.
     */
    @Test
    void le_responsable_import_voit_ses_dossiers_en_retard() throws Exception {
        String token = admin();
        String importateur = token("import@sogetherm.ma", "Import@123");

        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "TBR-IMP", "designation", "Produit import bord",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long fournisseurId = postId(token, "/api/fournisseurs", Map.of(
                "nom", "Fournisseur Bord", "email", "bord-fourn@test.local",
                "typeFournisseur", "ENTREPRISE", "raisonSociale", "Fournisseur Bord"));

        // Arrivee annoncee hier : le dossier est deja en retard
        long id = postId(importateur, "/api/commandes-fournisseur", Map.of(
                "fournisseurId", fournisseurId, "depotReceptionCode", "SH",
                "dateArriveePrevue", java.time.LocalDate.now().minusDays(1).toString(),
                "lignes", List.of(Map.of("produitId", produitId,
                        "quantiteCommandee", 5, "prixUnitaireDevise", 100))));

        // Un brouillon n'est pas en retard : rien n'est encore parti
        JsonNode avantEmission = tableauDeBord(importateur);
        assertFalse(contientFile(avantEmission, "Arrivees en retard"),
                "Un brouillon ne peut pas etre en retard d'arrivee");
        assertTrue(file(avantEmission, "Brouillons a emettre").get("total").asInt() > 0);

        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/emettre")
                        .header("Authorization", "Bearer " + importateur))
                .andExpect(status().isOk());

        JsonNode apres = tableauDeBord(importateur);
        JsonNode retard = file(apres, "Arrivees en retard");
        assertTrue(retard != null && retard.get("total").asInt() > 0,
                "Le dossier emis dont l'arrivee est passee doit remonter");
        assertTrue(file(apres, "En route").get("total").asInt() > 0);

        // Une fois receptionne, il quitte les deux files
        mockMvc.perform(post("/api/commandes-fournisseur/" + id + "/receptionner")
                        .header("Authorization", "Bearer " + importateur))
                .andExpect(status().isOk());
        JsonNode recu = tableauDeBord(importateur);
        assertFalse(contientFile(recu, "Arrivees en retard"));
        assertFalse(contientFile(recu, "En route"));
    }

    @Test
    void le_tableau_de_bord_exige_une_session() throws Exception {
        mockMvc.perform(get("/api/tableau-de-bord"))
                .andExpect(status().isUnauthorized());
    }
}

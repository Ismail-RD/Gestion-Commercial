package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hierarchie des roles : chacun n'accede qu'a son perimetre, et le commercial
 * est en plus cantonne a son propre portefeuille.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HierarchieRolesTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String admin;

    @BeforeEach
    void seConnecter() throws Exception {
        admin = token("admin@gestioncommerciale.local", "Admin@123");
    }

    private String token(String email, String motDePasse) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "motDePasse", motDePasse))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    /** Cree un compte avec le role voulu et renvoie son jeton. */
    private String compte(String role, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", role, "prenom", "Test", "email", email,
                                "motDePasse", "MotDePasse1", "role", role))))
                .andExpect(status().isOk());
        return token(email, "MotDePasse1");
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

    @Test
    void le_responsable_import_gere_le_catalogue_et_ignore_le_commercial() throws Exception {
        String importateur = compte("RESPONSABLE_IMPORT", "import@test.local");

        // Catalogue : autorise
        postId(importateur, "/api/produits", Map.of(
                "reference", "RI-1", "designation", "Produit import",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        postId(importateur, "/api/marques", Map.of("nom", "Marque Import"));

        // Commercial : hors de son perimetre
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + importateur))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/devis").header("Authorization", "Bearer " + importateur))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/factures").header("Authorization", "Bearer " + importateur))
                .andExpect(status().isForbidden());

        // Stock : lecture seule
        mockMvc.perform(get("/api/stock").header("Authorization", "Bearer " + importateur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + importateur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", 1, "depotCode", "SH", "quantite", 5))))
                .andExpect(status().isForbidden());
    }

    @Test
    void le_magasinier_gere_le_stock_mais_ne_saisit_pas_de_devis() throws Exception {
        String magasinier = compte("MAGASINIER", "magasinier@test.local");
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "MAG-1", "designation", "Produit magasin",
                "prixUnitaireHT", 50.0, "tauxTVA", 20));

        // Stock : ecriture autorisee
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        // Devis et clients : lecture seule
        mockMvc.perform(get("/api/devis").header("Authorization", "Bearer " + magasinier))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/devis")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", 2, "dateValidite", "2030-12-31",
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))))))
                .andExpect(status().isForbidden());

        // Catalogue : pas d'ecriture
        mockMvc.perform(post("/api/produits")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", "MAG-X", "designation", "Interdit",
                                "prixUnitaireHT", 10.0, "tauxTVA", 20))))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_commercial_ne_voit_pas_le_portefeuille_d_un_autre() throws Exception {
        String commercialA = compte("COMMERCIAL", "com-a@test.local");
        String commercialB = compte("COMMERCIAL", "com-b@test.local");

        long clientDeA = postId(commercialA, "/api/clients", Map.of(
                "nom", "Client de A", "email", "client-a@test.local", "typeClient", "PARTICULIER"));

        // A voit son client
        mockMvc.perform(get("/api/clients/" + clientDeA)
                        .header("Authorization", "Bearer " + commercialA))
                .andExpect(status().isOk());

        // B ne le voit pas, meme en connaissant son identifiant
        mockMvc.perform(get("/api/clients/" + clientDeA)
                        .header("Authorization", "Bearer " + commercialB))
                .andExpect(status().isForbidden());

        // Et il n'apparait pas dans sa liste
        mockMvc.perform(get("/api/clients")
                        .param("recherche", "Client de A")
                        .header("Authorization", "Bearer " + commercialB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Le responsable commercial, lui, voit tout le monde
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-com@test.local");
        mockMvc.perform(get("/api/clients/" + clientDeA)
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isOk());
    }

    @Test
    void le_commercial_ne_fixe_ni_prix_ni_tva() throws Exception {
        String commercial = compte("COMMERCIAL", "com-prix@test.local");
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "PRX-1", "designation", "Produit tarife",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Prix", "email", "client-prix@test.local", "typeClient", "PARTICULIER"));

        // Il tente 10 DH au lieu de 100, et 5 % de TVA au lieu de 20
        long devisId = postId(commercial, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                        "prixUnitaire", 10.0, "tauxTVA", 5))));

        // Le catalogue s'impose : 2 x 100 = 200 HT, TVA 20 %
        mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(100.00))
                .andExpect(jsonPath("$.lignes[0].tauxTVA").value(20.00))
                .andExpect(jsonPath("$.montantHT").value(200.00));

        // Le responsable commercial, lui, negocie librement
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-prix@test.local");
        long devisNegocie = postId(responsable, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                        "prixUnitaire", 80.0, "tauxTVA", 20))));
        mockMvc.perform(get("/api/devis/" + devisNegocie)
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(80.00))
                .andExpect(jsonPath("$.montantHT").value(160.00));
    }

    @Test
    void le_responsable_commercial_valide_les_remises_mais_pas_le_commercial() throws Exception {
        String commercial = compte("COMMERCIAL", "com-remise@test.local");
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-remise@test.local");
        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "RMS-1", "designation", "Produit remise role",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Remise Role", "email", "remise-role@test.local",
                "typeClient", "PARTICULIER"));

        long devisId = postId(commercial, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1, "remise", 30))));

        // Remise au-dela du seuil : le devis attend l'aval de l'encadrement des
        // sa saisie, l'envoi ne lui est meme pas propose.
        mockMvc.perform(get("/api/devis/" + devisId)
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_VALIDATION"));

        // Le commercial ne peut pas se valider lui-meme
        mockMvc.perform(post("/api/devis/" + devisId + "/valider-remise")
                        .header("Authorization", "Bearer " + commercial))
                .andExpect(status().isForbidden());

        // Le responsable commercial, si : le devis reste un brouillon, mais
        // l'envoi lui est rouvert.
        mockMvc.perform(post("/api/devis/" + devisId + "/valider-remise")
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.remiseAValider").value(false));
    }

    /**
     * Traiter une commande acceptee est le metier du magasinier : le responsable
     * commercial ne valide pas, ne prepare pas et ne livre pas. Il garde en
     * revanche la main sur l'annulation, qui est une decision commerciale.
     */
    @Test
    void le_responsable_commercial_ne_traite_pas_les_commandes() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-logistique@test.local");
        String magasinier = compte("MAGASINIER", "mag-logistique@test.local");

        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "LOG-1", "designation", "Produit logistique",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long clientId = postId(responsable, "/api/clients", Map.of(
                "nom", "Client Logistique", "email", "logistique@test.local",
                "typeClient", "PARTICULIER"));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 20))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))))))
                .andExpect(status().isCreated())
                .andReturn();
        var cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        String validation = objectMapper.writeValueAsString(Map.of(
                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))));

        // Le responsable commercial ne valide pas : c'est une prise de stock
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON).content(validation))
                .andExpect(status().isForbidden());

        // Le magasinier, si
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON).content(validation))
                .andExpect(status().isOk());

        // Ni preparation ni livraison pour le responsable commercial
        for (String etape : List.of("EN_PREPARATION", "LIVREE")) {
            mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                            .header("Authorization", "Bearer " + responsable)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("statut", etape))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "EN_PREPARATION"))))
                .andExpect(status().isOk());

        // Le magasinier n'annule pas : cela releve du commercial
        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "ANNULEE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/commandes/" + commandeId + "/statut")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "ANNULEE"))))
                .andExpect(status().isOk());
    }

    /**
     * Un portefeuille se confie a la force de vente. L'attribuer a un magasinier
     * le rendrait invisible a tous, puisque la propriete suit le commercial.
     */
    @Test
    void un_client_ne_s_attribue_qu_a_un_commercial() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-attrib@test.local");
        String commercial = compte("COMMERCIAL", "com-attrib@test.local");
        compte("MAGASINIER", "mag-attrib@test.local");

        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Attribue", "email", "attrib@test.local", "typeClient", "PARTICULIER"));

        MvcResult utilisateurs = mockMvc.perform(get("/api/utilisateurs")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        var comptes = objectMapper.readTree(utilisateurs.getResponse().getContentAsString());
        long idMagasinier = 0;
        long idResponsable = 0;
        for (var u : comptes) {
            if ("mag-attrib@test.local".equals(u.get("email").asText())) {
                idMagasinier = u.get("id").asLong();
            }
            if ("resp-attrib@test.local".equals(u.get("email").asText())) {
                idResponsable = u.get("id").asLong();
            }
        }

        // Vers un magasinier : refuse
        mockMvc.perform(patch("/api/clients/" + clientId + "/commercial")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commercialId", idMagasinier))))
                .andExpect(status().isConflict());

        // Le responsable commercial repartit librement entre commerciaux
        mockMvc.perform(patch("/api/clients/" + clientId + "/commercial")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commercialId", idResponsable))))
                .andExpect(status().isOk());

        // Un commercial ne reattribue pas : c'est une decision d'encadrement
        mockMvc.perform(patch("/api/clients/" + clientId + "/commercial")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commercialId", idResponsable))))
                .andExpect(status().isForbidden());
    }

    /**
     * L'emetteur imprime sur un bon de livraison est celui qui edite le
     * document, jamais le createur de la commande.
     */
    @Test
    void l_emetteur_du_bon_de_livraison_est_celui_qui_l_imprime() throws Exception {
        String commercial = compte("COMMERCIAL", "com-emetteur@test.local");
        String magasinier = compte("MAGASINIER", "mag-emetteur@test.local");

        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "EMT-1", "designation", "Produit emetteur",
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long clientId = postId(commercial, "/api/clients", Map.of(
                "nom", "Client Emetteur", "email", "emetteur@test.local",
                "typeClient", "PARTICULIER"));
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 10))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))))))
                .andExpect(status().isCreated())
                .andReturn();
        var cmd = objectMapper.readTree(res.getResponse().getContentAsString());
        long commandeId = cmd.get("id").asLong();
        long ligneId = cmd.get("lignes").get(0).get("id").asLong();

        // Le magasinier valide la commande
        mockMvc.perform(post("/api/commandes/" + commandeId + "/valider")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "lignes", List.of(Map.of("ligneId", ligneId, "depotCode", "SH"))))))
                .andExpect(status().isOk());

        // Le nom du magasinier n'a qu'une raison de figurer sur ce bon : etre
        // l'emetteur. Celui du commercial y est de toute facon, en Representant.
        String parMagasinier = texteDuPdf(bonLivraison(commandeId, magasinier));
        assertTrue(parMagasinier.contains("Test MAGASINIER"),
                "Imprime par le magasinier, le bon devrait porter son nom");

        // L'admin imprime la meme commande : l'emetteur bascule sur lui, bien
        // qu'elle ait ete traitee par le magasinier et creee par le commercial.
        String parAdmin = texteDuPdf(bonLivraison(commandeId, admin));
        assertTrue(parAdmin.contains("Systeme Admin"),
                "Imprime par l'admin, le bon devrait porter son nom");
        assertFalse(parAdmin.contains("Test MAGASINIER"),
                "Le magasinier ne doit plus figurer quand un autre imprime le bon");
    }

    private byte[] bonLivraison(long commandeId, String token) throws Exception {
        return mockMvc.perform(get("/api/commandes/" + commandeId + "/bon-livraison")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private byte[] facturePdf(long factureId, String token) throws Exception {
        return mockMvc.perform(get("/api/factures/" + factureId + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String texteDuPdf(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
    }

    /**
     * Le comptable tient la facturation de bout en bout (emission, echeance,
     * encaissement) et lit le reste sans y toucher.
     */
    @Test
    void le_comptable_facture_mais_ne_vend_pas() throws Exception {
        String comptable = compte("COMPTABLE", "comptable@test.local");
        String magasinier = compte("MAGASINIER", "mag-compta@test.local");

        long produitId = postId(admin, "/api/produits", Map.of(
                "reference", "CPT-1", "designation", "Produit facture",
                "prixUnitaireHT", 200.0, "tauxTVA", 20));
        long clientId = postId(admin, "/api/clients", Map.of(
                "nom", "Client Compta", "email", "compta@test.local",
                "typeClient", "PARTICULIER"));
        long commandeId = postId(admin, "/api/commandes", Map.of(
                "clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 3))));

        // Lecture des documents et du catalogue : ouverte
        for (String url : List.of("/api/factures", "/api/commandes", "/api/devis",
                "/api/clients", "/api/produits", "/api/marques", "/api/categories")) {
            mockMvc.perform(get(url).header("Authorization", "Bearer " + comptable))
                    .andExpect(status().isOk());
        }

        // Facturation : le cycle complet lui appartient
        long factureId = postId(comptable, "/api/factures", Map.of(
                "commandeId", commandeId, "dateEcheance", "2030-12-31"));
        mockMvc.perform(put("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dateEcheance", "2031-01-31"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateEcheance").value("2031-01-31"));
        mockMvc.perform(delete("/api/factures/" + factureId)
                        .header("Authorization", "Bearer " + comptable))
                .andExpect(status().isNoContent());

        // Encaissement (sur une facture reemise : une facture reglee ne s'efface plus)
        long factureReglee = postId(comptable, "/api/factures", Map.of(
                "commandeId", commandeId, "dateEcheance", "2030-12-31"));
        mockMvc.perform(post("/api/paiements")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "factureId", factureReglee, "montant", 100.0,
                                "modePaiement", "VIREMENT"))))
                .andExpect(status().isCreated());

        // L'emetteur de la facture est celui qui l'imprime, comme sur le BL :
        // la commande a beau avoir ete saisie par l'admin, c'est le comptable
        // qui sort le document.
        String parComptable = texteDuPdf(facturePdf(factureReglee, comptable));
        assertTrue(parComptable.contains("Test COMPTABLE"),
                "Le comptable qui imprime doit figurer comme emetteur : " + parComptable);
        String parAdmin = texteDuPdf(facturePdf(factureReglee, admin));
        assertTrue(parAdmin.contains("Systeme Admin"),
                "L'admin qui imprime doit figurer comme emetteur : " + parAdmin);
        assertFalse(parAdmin.contains("Test COMPTABLE"),
                "Le comptable ne doit plus apparaitre quand l'admin imprime");

        // Vente, catalogue et stock : lecture seule
        mockMvc.perform(post("/api/devis")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId, "dateValidite", "2030-12-31",
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/commandes")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientId", clientId,
                                "lignes", List.of(Map.of("produitId", produitId, "quantite", 1))))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Interdit", "email", "interdit@test.local",
                                "typeClient", "PARTICULIER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/produits")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", "CPT-X", "designation", "Interdit",
                                "prixUnitaireHT", 10.0, "tauxTVA", 20))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/stock/entree")
                        .header("Authorization", "Bearer " + comptable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "produitId", produitId, "depotCode", "SH", "quantite", 5))))
                .andExpect(status().isForbidden());

        // Et la facturation ne s'est pas ouverte au passage aux autres roles
        mockMvc.perform(post("/api/factures")
                        .header("Authorization", "Bearer " + magasinier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "commandeId", commandeId, "dateEcheance", "2030-12-31"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void seul_l_admin_cree_des_comptes() throws Exception {
        String responsable = compte("RESPONSABLE_COMMERCIAL", "resp-compte@test.local");

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Pirate", "prenom", "Test", "email", "pirate@test.local",
                                "motDePasse", "MotDePasse1", "role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }
}

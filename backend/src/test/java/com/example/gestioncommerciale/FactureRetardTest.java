package com.example.gestioncommerciale;

import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.StatutFacture;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.service.SurveillanceEcheances;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EN_RETARD est un statut a part entiere : il suit l'echeance, l'encaissement et
 * le passage du temps.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FactureRetardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FactureRepository factureRepository;

    @Autowired
    private SurveillanceEcheances surveillanceEcheances;

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

    /** Cree une facture de 240 DH TTC avec l'echeance voulue. */
    private long facture(String token, String reference, LocalDate echeance) throws Exception {
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client " + reference, "email", reference + "@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", reference, "designation", "Produit " + reference,
                "prixUnitaireHT", 100.0, "tauxTVA", 20));
        long commandeId = postId(token, "/api/commandes", Map.of(
                "clientId", clientId,
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2))));
        return postId(token, "/api/factures", Map.of(
                "commandeId", commandeId, "dateEcheance", echeance.toString()));
    }

    @Test
    void une_facture_emise_avec_une_echeance_passee_nait_en_retard() throws Exception {
        long id = facture(token(), "RET-1", LocalDate.now().minusDays(3));

        assertEquals(StatutFacture.EN_RETARD,
                factureRepository.findById(id).orElseThrow().getStatut());
    }

    /** Le balayage remplace le seul evenement qui n'existe pas : le temps qui passe. */
    @Test
    void le_balayage_fait_basculer_les_echeances_depassees() throws Exception {
        long id = facture(token(), "RET-2", LocalDate.now().plusDays(30));
        assertEquals(StatutFacture.EMISE,
                factureRepository.findById(id).orElseThrow().getStatut());

        // On avance l'echeance dans le passe sans passer par le service, comme
        // le ferait le simple ecoulement du temps.
        Facture facture = factureRepository.findById(id).orElseThrow();
        facture.setDateEcheance(LocalDate.now().minusDays(1));
        factureRepository.save(facture);

        surveillanceEcheances.marquerLesFacturesEchues();

        assertEquals(StatutFacture.EN_RETARD,
                factureRepository.findById(id).orElseThrow().getStatut());
    }

    @Test
    void repousser_l_echeance_sort_la_facture_du_retard() throws Exception {
        String token = token();
        long id = facture(token, "RET-3", LocalDate.now().minusDays(2));
        assertEquals(StatutFacture.EN_RETARD,
                factureRepository.findById(id).orElseThrow().getStatut());

        mockMvc.perform(put("/api/factures/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dateEcheance", LocalDate.now().plusMonths(1).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EMISE"));
    }

    /**
     * Le retard prime sur le paiement partiel : c'est lui qui appelle une
     * relance. Solder la facture la sort des deux etats.
     */
    @Test
    void un_paiement_partiel_ne_masque_pas_le_retard() throws Exception {
        String token = token();
        long id = facture(token, "RET-4", LocalDate.now().minusDays(5));

        postId(token, "/api/paiements", Map.of(
                "factureId", id, "montant", 100.0, "modePaiement", "ESPECES"));
        Facture apresAcompte = factureRepository.findById(id).orElseThrow();
        assertEquals(StatutFacture.EN_RETARD, apresAcompte.getStatut());
        assertEquals(0, new BigDecimal("100.00").compareTo(apresAcompte.getMontantPaye()));

        postId(token, "/api/paiements", Map.of(
                "factureId", id, "montant", 140.0, "modePaiement", "ESPECES"));
        assertEquals(StatutFacture.PAYEE,
                factureRepository.findById(id).orElseThrow().getStatut());
    }

    /** Une facture annulee reste annulee : c'est une decision, pas un etat deduit. */
    @Test
    void une_facture_annulee_ne_bascule_pas_en_retard() throws Exception {
        long id = facture(token(), "RET-5", LocalDate.now().plusDays(10));

        Facture facture = factureRepository.findById(id).orElseThrow();
        facture.setStatut(StatutFacture.ANNULEE);
        facture.setDateEcheance(LocalDate.now().minusDays(1));
        factureRepository.save(facture);

        surveillanceEcheances.marquerLesFacturesEchues();

        assertEquals(StatutFacture.ANNULEE,
                factureRepository.findById(id).orElseThrow().getStatut());
    }
}

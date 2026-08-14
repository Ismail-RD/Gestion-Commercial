package com.example.gestioncommerciale;

import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espace client : le client consulte son devis via le jeton du lien recu par
 * email, puis accepte en deposant son bon de commande.
 *
 * Point cle verifie ici : sa reponse est tracee mais ne change PAS le statut du
 * devis, l'acceptation restant une decision manuelle dans l'application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DevisEspaceClientTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevisRepository devisRepository;

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

    /** Cree un devis envoye et lui pose un jeton, comme le ferait l'envoi email. */
    private String devisAvecJeton(String token, String suffixe) throws Exception {
        long clientId = postId(token, "/api/clients", Map.of(
                "nom", "Client Espace " + suffixe, "email", "espace" + suffixe + "@test.local",
                "typeClient", "PARTICULIER"));
        long produitId = postId(token, "/api/produits", Map.of(
                "reference", "ESP-" + suffixe, "designation", "Produit espace client",
                "prixUnitaireHT", 100.0, "tauxTVA", 20, "uniteMesure", "PIECE"));
        long devisId = postId(token, "/api/devis", Map.of(
                "clientId", clientId, "dateValidite", "2030-12-31",
                "lignes", List.of(Map.of("produitId", produitId, "quantite", 2,
                        "prixUnitaire", 100.0, "tauxTVA", 20))));
        mockMvc.perform(post("/api/devis/" + devisId + "/envoyer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        Devis devis = devisRepository.findById(devisId).orElseThrow();
        String jeton = UUID.randomUUID().toString().replace("-", "");
        devis.setTokenClient(jeton);
        devisRepository.save(devis);
        return jeton;
    }

    @Test
    void le_client_accepte_en_deposant_son_bon_de_commande_sans_changer_le_statut() throws Exception {
        String token = token();
        String jeton = devisAvecJeton(token, "A");

        // Consultation publique, sans authentification
        mockMvc.perform(get("/api/public/devis/" + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponseClient").doesNotExist())
                .andExpect(jsonPath("$.bonCommandeDepose").value(false));

        // Le bon de commande est obligatoire pour accepter
        mockMvc.perform(multipart("/api/public/devis/" + jeton + "/accepter")
                        .file(new MockMultipartFile("bonCommande", "vide.pdf",
                                "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/public/devis/" + jeton + "/accepter")
                        .file(new MockMultipartFile("bonCommande", "bc.pdf",
                                "application/pdf", "%PDF-1.4 bon de commande".getBytes())))
                .andExpect(status().isNoContent());

        // La reponse est tracee, mais le devis reste ENVOYE : validation manuelle
        Devis devis = devisRepository.findByTokenClient(jeton).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ENVOYE", devis.getStatut().name());
        org.junit.jupiter.api.Assertions.assertEquals("ACCEPTE", devis.getReponseClient().name());

        // Le gestionnaire telecharge le bon de commande pour verification
        mockMvc.perform(get("/api/devis/" + devis.getId() + "/bon-commande")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Une seule reponse par lien
        mockMvc.perform(post("/api/public/devis/" + jeton + "/refuser"))
                .andExpect(status().isConflict());
    }

    @Test
    void un_lien_inconnu_est_refuse() throws Exception {
        mockMvc.perform(get("/api/public/devis/jeton-inexistant"))
                .andExpect(status().isNotFound());
    }
}

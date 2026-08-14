package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fiche technique : upload (PDF/image) stocke sur disque, chemin en base ;
 * telechargement du contenu ; refus des types non autorises.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FicheTechniqueTest {

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

    private long creerProduit(String token, String ref) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/produits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reference", ref, "designation", "Produit " + ref,
                                "prixUnitaireHT", 100.0, "tauxTVA", 20))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void upload_puis_telechargement_de_la_fiche_technique() throws Exception {
        String token = token();
        long produitId = creerProduit(token, "FICHE-1");

        byte[] contenu = "%PDF-1.4 contenu de test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "fiche.pdf", "application/pdf", contenu);

        mockMvc.perform(multipart("/api/produits/" + produitId + "/fiche-technique")
                        .file(fichier)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ficheTechnique").isNotEmpty());

        mockMvc.perform(get("/api/produits/" + produitId + "/fiche-technique")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(contenu));
    }

    @Test
    void un_type_non_autorise_est_refuse() throws Exception {
        String token = token();
        long produitId = creerProduit(token, "FICHE-2");

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "note.txt", "text/plain",
                "juste du texte".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/produits/" + produitId + "/fiche-technique")
                        .file(fichier)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());
    }
}

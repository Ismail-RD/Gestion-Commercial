package com.example.gestioncommerciale;

import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD des comptes par l'administrateur, et ses garde-fous. */
@SpringBootTest
@AutoConfigureMockMvc
class GestionUtilisateursTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

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

    /** Cree un compte actif et renvoie son identifiant. */
    private long compte(String role, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", role, "prenom", "Gestion", "email", email,
                                "motDePasse", "MotDePasse1", "role", role))))
                .andExpect(status().isOk());
        return utilisateurRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst().orElseThrow().getId();
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
    void l_administrateur_modifie_et_supprime_un_compte() throws Exception {
        long id = compte("MAGASINIER", "crud-mag@test.local");

        mockMvc.perform(get("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("crud-mag@test.local"));

        mockMvc.perform(put("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Corrige", "prenom", "Prenom",
                                "email", "crud-mag2@test.local", "role", "COMPTABLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Corrige"))
                .andExpect(jsonPath("$.email").value("crud-mag2@test.local"))
                .andExpect(jsonPath("$.role").value("COMPTABLE"));

        // Un compte vierge de toute trace s'efface
        mockMvc.perform(delete("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNoContent());
        assertFalse(utilisateurRepository.existsByEmail("crud-mag2@test.local"));
    }

    @Test
    void une_adresse_deja_prise_est_refusee_a_la_modification() throws Exception {
        long id = compte("MAGASINIER", "crud-doublon@test.local");

        mockMvc.perform(put("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "X", "prenom", "Y",
                                "email", "m.benali@sogetherm.ma", "role", "MAGASINIER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void un_commercial_titulaire_d_un_portefeuille_ne_s_efface_pas() throws Exception {
        long id = compte("COMMERCIAL", "crud-com@test.local");
        String commercial = token("crud-com@test.local", "MotDePasse1");
        postId(commercial, "/api/clients", Map.of(
                "nom", "Client CRUD User", "email", "crud-user-cli@test.local",
                "typeClient", "PARTICULIER"));

        mockMvc.perform(delete("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isConflict());

        // Ni ne sort de la force de vente : son portefeuille deviendrait invisible
        mockMvc.perform(put("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Com", "prenom", "Crud",
                                "email", "crud-com@test.local", "role", "MAGASINIER"))))
                .andExpect(status().isConflict());

        // En revanche il se desactive : l'acces tombe, l'historique reste
        mockMvc.perform(patch("/api/utilisateurs/" + id + "/activation")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actif", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "crud-com@test.local", "motDePasse", "MotDePasse1"))))
                .andExpect(status().is4xxClientError());

        // Et se reactive
        mockMvc.perform(patch("/api/utilisateurs/" + id + "/activation")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actif", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));
    }

    /** Un invite n'a pas de mot de passe : l'activer donnerait un compte inutilisable. */
    @Test
    void on_n_active_pas_un_invite_qui_n_a_pas_repondu() throws Exception {
        Utilisateur invite = utilisateurRepository.save(Utilisateur.builder()
                .nom("Sans").prenom("MotDePasse").email("crud-invite@test.local")
                .role(Role.COMMERCIAL).actif(false)
                .tokenInvitation("jetoncrudinvite0001")
                .invitationExpireLe(java.time.LocalDateTime.now().plusDays(7))
                .build());

        mockMvc.perform(patch("/api/utilisateurs/" + invite.getId() + "/activation")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actif", true))))
                .andExpect(status().isConflict());
    }

    /** L'administrateur ne se retire pas ses propres moyens d'agir. */
    @Test
    void l_administrateur_ne_se_verrouille_pas_dehors() throws Exception {
        Utilisateur moi = utilisateurRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("admin@gestioncommerciale.local"))
                .findFirst().orElseThrow();

        mockMvc.perform(delete("/api/utilisateurs/" + moi.getId())
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/utilisateurs/" + moi.getId() + "/activation")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actif", false))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/utilisateurs/" + moi.getId())
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Admin", "prenom", "Systeme",
                                "email", "admin@gestioncommerciale.local", "role", "COMMERCIAL"))))
                .andExpect(status().isConflict());
    }

    @Test
    void le_crud_des_comptes_est_reserve_a_l_administrateur() throws Exception {
        long id = compte("MAGASINIER", "crud-droits@test.local");
        String responsable = token("m.benali@sogetherm.ma", "Commercial@123");

        mockMvc.perform(put("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "X", "prenom", "Y",
                                "email", "pirate@test.local", "role", "ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/utilisateurs/" + id)
                        .header("Authorization", "Bearer " + responsable))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/utilisateurs/" + id + "/activation")
                        .header("Authorization", "Bearer " + responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actif", false))))
                .andExpect(status().isForbidden());
    }
}

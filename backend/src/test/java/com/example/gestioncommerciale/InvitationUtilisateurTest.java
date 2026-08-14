package com.example.gestioncommerciale;

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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creation d'un compte par invitation. Aucun serveur SMTP en test : l'envoi
 * echoue en 503 et, l'envoi faisant partie de la transaction, le compte n'est
 * pas cree. Les etapes suivantes partent donc d'une invitation posee
 * directement en base.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvitationUtilisateurTest {

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

    /** Pose un invite avec son jeton, comme le ferait un envoi d'email reussi. */
    private Utilisateur inviteEnAttente(String email, LocalDateTime expiration) {
        return utilisateurRepository.save(Utilisateur.builder()
                .nom("Nouvel").prenom("Invite").email(email)
                .role(com.example.gestioncommerciale.entity.Role.COMMERCIAL)
                .actif(false)
                .tokenInvitation(UUID.randomUUID().toString().replace("-", ""))
                .invitationExpireLe(expiration)
                .build());
    }

    @Test
    void seul_l_administrateur_invite_un_utilisateur() throws Exception {
        String commercial = token("m.benali@sogetherm.ma", "Commercial@123");

        mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + commercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Pirate", "prenom", "Test",
                                "email", "pirate-invite@test.local", "role", "ADMIN"))))
                .andExpect(status().isForbidden());

        // L'admin, lui, passe le controle d'acces : c'est l'envoi qui bloque,
        // faute de serveur SMTP en test.
        mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Nouveau", "prenom", "Compte",
                                "email", "nouveau-compte@test.local", "role", "COMMERCIAL"))))
                .andExpect(status().isServiceUnavailable());

        // L'envoi ayant echoue, rien n'est reste en base
        assertFalse(utilisateurRepository.existsByEmail("nouveau-compte@test.local"),
                "Le compte ne doit pas subsister si l'invitation n'est pas partie");
    }

    @Test
    void une_adresse_deja_utilisee_est_refusee() throws Exception {
        mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nom", "Doublon", "prenom", "Test",
                                "email", "m.benali@sogetherm.ma", "role", "COMMERCIAL"))))
                .andExpect(status().isConflict());
    }

    @Test
    void l_invite_choisit_son_mot_de_passe_et_le_compte_s_active() throws Exception {
        Utilisateur invite = inviteEnAttente("invite-ok@test.local",
                LocalDateTime.now().plusDays(7));
        String lien = invite.getTokenInvitation();

        // Tant qu'il n'a pas repondu, le compte est inactif : pas de connexion
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invite-ok@test.local", "motDePasse", "MotDePasse1"))))
                .andExpect(status().is4xxClientError());

        // La page d'inscription est ouverte : elle rappelle qui est attendu
        mockMvc.perform(get("/api/public/invitations/" + lien))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("invite-ok@test.local"))
                .andExpect(jsonPath("$.role").value("COMMERCIAL"));

        // Un mot de passe trop court est refuse
        mockMvc.perform(post("/api/public/invitations/" + lien + "/mot-de-passe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motDePasse", "court"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/public/invitations/" + lien + "/mot-de-passe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("motDePasse", "MotDePasse1"))))
                .andExpect(status().isNoContent());

        // Le compte est actif et le jeton efface : le lien ne resservira pas
        Utilisateur apres = utilisateurRepository.findById(invite.getId()).orElseThrow();
        assertTrue(apres.isActif(), "Le compte doit etre actif apres la reponse");
        assertNull(apres.getTokenInvitation(), "Le jeton doit etre efface apres usage");

        mockMvc.perform(get("/api/public/invitations/" + lien))
                .andExpect(status().isNotFound());

        // Et il se connecte avec le mot de passe qu'il a choisi
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invite-ok@test.local", "motDePasse", "MotDePasse1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void une_invitation_expiree_ne_vaut_plus_rien() throws Exception {
        Utilisateur invite = inviteEnAttente("invite-expire@test.local",
                LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/public/invitations/" + invite.getTokenInvitation()))
                .andExpect(status().isGone());

        mockMvc.perform(post("/api/public/invitations/" + invite.getTokenInvitation()
                        + "/mot-de-passe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("motDePasse", "MotDePasse1"))))
                .andExpect(status().isGone());
    }

    @Test
    void un_jeton_inconnu_ne_revele_rien() throws Exception {
        mockMvc.perform(get("/api/public/invitations/jeton-invente"))
                .andExpect(status().isNotFound());
    }

    @Test
    void on_ne_renvoie_pas_d_invitation_a_un_compte_deja_actif() throws Exception {
        Utilisateur actif = utilisateurRepository.findAll().stream()
                .filter(Utilisateur::isActif)
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/utilisateurs/" + actif.getId() + "/renvoyer-invitation")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isConflict());
    }
}

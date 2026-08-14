package com.example.gestioncommerciale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Jeton d'un compte existant, pour les appels qui exigent une identite. */
    private String token(String email, String motDePasse) throws Exception {
        var res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "motDePasse", motDePasse))))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void inscription_puis_connexion_renvoie_un_token() throws Exception {
        // La creation de compte porte le role du nouvel utilisateur : elle est
        // reservee a l'admin, sinon n'importe qui se fabriquerait un compte ADMIN.
        String admin = token("admin@gestioncommerciale.local", "Admin@123");

        Map<String, Object> inscription = Map.of(
                "nom", "Doe",
                "prenom", "John",
                "email", "john.doe@test.local",
                "motDePasse", "MotDePasse1",
                "role", "COMMERCIAL"
        );

        // Sans compte admin, l'inscription est refusee
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inscription)))
                .andExpect(status().isUnauthorized());

        // register
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inscription)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.email", is("john.doe@test.local")))
                .andExpect(jsonPath("$.role", is("COMMERCIAL")));

        // login
        Map<String, String> login = Map.of(
                "email", "john.doe@test.local",
                "motDePasse", "MotDePasse1"
        );
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void acces_refuse_sans_token() throws Exception {
        mockMvc.perform(get("/api/produits"))
                .andExpect(status().isUnauthorized());
    }
}

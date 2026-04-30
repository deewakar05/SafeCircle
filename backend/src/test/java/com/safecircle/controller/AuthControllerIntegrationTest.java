package com.safecircle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecircle.dto.AuthRequest.SignupRequest;
import com.safecircle.dto.AuthRequest.LoginRequest;
import com.safecircle.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController}.
 *
 * <p>Uses embedded MongoDB (flapdoodle) and a real Spring context to exercise
 * the complete stack: HTTP → Controller → Service → Repository → DB.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Auth Controller Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    // ── Signup ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/signup → 200 with token")
    void signup_success() throws Exception {
        SignupRequest req = new SignupRequest("Bob", "bob@example.com", "secret123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/signup → 400 on duplicate email")
    void signup_duplicateEmail() throws Exception {
        SignupRequest req = new SignupRequest("Bob", "bob@example.com", "secret123");
        // Register once
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)));

        // Second attempt with the same email
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/signup → 400 on invalid email format")
    void signup_invalidEmail() throws Exception {
        String body = """
                {"name":"Bob","email":"not-an-email","password":"secret123"}
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/signup → 400 on short password")
    void signup_shortPassword() throws Exception {
        String body = """
                {"name":"Bob","email":"bob@example.com","password":"ab"}
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login → 200 with token after signup")
    void login_success() throws Exception {
        // First sign up
        SignupRequest signup = new SignupRequest("Carol", "carol@example.com", "pass1234");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(signup)));

        // Then log in
        LoginRequest login = new LoginRequest("carol@example.com", "pass1234");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/login → 401 with wrong password")
    void login_wrongPassword() throws Exception {
        SignupRequest signup = new SignupRequest("Dave", "dave@example.com", "correct");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(signup)));

        LoginRequest login = new LoginRequest("dave@example.com", "wrong-password");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login → 401 with unknown email")
    void login_unknownEmail() throws Exception {
        LoginRequest login = new LoginRequest("nobody@example.com", "pass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    // ── JWT usage ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("JWT from signup can authenticate a protected endpoint")
    void jwtFromSignup_canAccessProtectedEndpoint() throws Exception {
        SignupRequest req = new SignupRequest("Eve", "eve@example.com", "password99");
        MvcResult res = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andReturn();

        String token = mapper.readTree(res.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(token).isNotBlank();

        // Access a protected endpoint with the JWT
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/groups/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoint rejects missing token with 403")
    void protectedEndpoint_noToken_rejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/groups/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint rejects expired/tampered token with 403")
    void protectedEndpoint_invalidToken_rejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/groups/my")
                        .header("Authorization", "Bearer this.is.a.tampered.token"))
                .andExpect(status().isForbidden());
    }
}

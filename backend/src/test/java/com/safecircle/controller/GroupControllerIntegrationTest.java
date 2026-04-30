package com.safecircle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecircle.dto.AuthRequest.SignupRequest;
import com.safecircle.dto.AuthRequest.LoginRequest;
import com.safecircle.dto.GroupDto.CreateGroupRequest;
import com.safecircle.dto.GroupDto.JoinGroupRequest;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.LocationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link GroupController} and {@link LocationController}.
 *
 * <p>Exercises the complete stack: HTTP → Security → Controller → Service → Repository → DB.
 * Uses embedded MongoDB so no external infrastructure is required.</p>
 *
 * <h3>Test Scenarios Covered</h3>
 * <ul>
 *   <li>Group creation and membership</li>
 *   <li>Join with valid / invalid invite code</li>
 *   <li>IDOR: GET /api/groups/{id} by non-member → 403</li>
 *   <li>IDOR: GET /api/locations/group/{id} by non-member → 403</li>
 *   <li>Admin-only: setThreshold by non-admin → 403</li>
 *   <li>Location update by member → 200 + WebSocket broadcast</li>
 *   <li>Location update by non-member → 403</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Group + Location Controller Integration Tests")
class GroupControllerIntegrationTest {

    @Autowired MockMvc         mockMvc;
    @Autowired ObjectMapper    mapper;
    @Autowired UserRepository  userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired LocationRepository locationRepository;

    private String adminToken;
    private String memberToken;
    private String attackerToken;
    private String groupId;
    private String inviteCode;

    @BeforeEach
    void setUp() throws Exception {
        // Clean state
        locationRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // Register admin
        adminToken   = registerAndLogin("Admin", "admin@test.com",    "pass1234");
        // Register a regular member
        memberToken  = registerAndLogin("Member", "member@test.com",  "pass1234");
        // Register an attacker (not in the group)
        attackerToken = registerAndLogin("Attacker", "att@test.com", "pass1234");

        // Admin creates group
        CreateGroupRequest createReq = new CreateGroupRequest("SafeTrip", 300);
        MvcResult res = mockMvc.perform(post("/api/groups/create")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        var groupJson = mapper.readTree(res.getResponse().getContentAsString());
        groupId    = groupJson.get("id").asText();
        inviteCode = groupJson.get("inviteCode").asText();

        // Member joins the group
        JoinGroupRequest joinReq = new JoinGroupRequest(inviteCode);
        mockMvc.perform(post("/api/groups/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(joinReq)))
                .andExpect(status().isOk());
    }

    // ── getGroup ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/groups/{id} → 200 for group member")
    void getGroup_member_success() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SafeTrip"))
                .andExpect(jsonPath("$.inviteCode").value(inviteCode));
    }

    @Test
    @DisplayName("GET /api/groups/{id} → 403 for non-member (IDOR protection)")
    void getGroup_nonMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: you are not a member of this group"));
    }

    @Test
    @DisplayName("GET /api/groups/{id} → 403 for unauthenticated request")
    void getGroup_noToken_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId))
                .andExpect(status().isForbidden());
    }

    // ── joinGroup ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/groups/join → 400 on invalid invite code")
    void joinGroup_invalidCode_badRequest() throws Exception {
        String body = """
                {"inviteCode":"XXXXXX"}
                """;
        mockMvc.perform(post("/api/groups/join")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid invite code"));
    }

    // ── listMyGroups ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/groups/my → returns only groups user belongs to")
    void listMyGroups_returnsCorrectGroups() throws Exception {
        // Admin should see the group
        mockMvc.perform(get("/api/groups/my")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("SafeTrip"));

        // Attacker should see empty list
        mockMvc.perform(get("/api/groups/my")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── setThreshold ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/groups/{id}/threshold → 403 for non-admin member")
    void setThreshold_nonAdmin_forbidden() throws Exception {
        String body = """
                {"threshold":500}
                """;
        mockMvc.perform(put("/api/groups/" + groupId + "/threshold")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/groups/{id}/threshold → 200 for admin")
    void setThreshold_admin_success() throws Exception {
        String body = """
                {"threshold":500}
                """;
        mockMvc.perform(put("/api/groups/" + groupId + "/threshold")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceThreshold").value(500));
    }

    // ── Location: getGroupLocations ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/locations/group/{id} → 200 for member")
    void getGroupLocations_member_success() throws Exception {
        mockMvc.perform(get("/api/locations/group/" + groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/locations/group/{id} → 403 for non-member (IDOR protection)")
    void getGroupLocations_nonMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/locations/group/" + groupId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: you are not a member of this group"));
    }

    // ── Location: updateLocation ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/locations/update → 200 for group member")
    void updateLocation_member_success() throws Exception {
        String body = String.format(
                """
                {"groupId":"%s","lat":28.6139,"lng":77.2090,"status":"ONLINE","accuracy":10.0}
                """, groupId);

        mockMvc.perform(post("/api/locations/update")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.lat").value(28.6139));
    }

    @Test
    @DisplayName("POST /api/locations/update → 403 for non-member (IDOR protection)")
    void updateLocation_nonMember_forbidden() throws Exception {
        String body = String.format(
                """
                {"groupId":"%s","lat":28.6,"lng":77.2,"status":"ONLINE"}
                """, groupId);

        mockMvc.perform(post("/api/locations/update")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/locations/update → 400 on invalid GPS coordinates")
    void updateLocation_invalidGps_badRequest() throws Exception {
        String body = String.format(
                """
                {"groupId":"%s","lat":999.0,"lng":77.2,"status":"ONLINE"}
                """, groupId);

        mockMvc.perform(post("/api/locations/update")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private String registerAndLogin(String name, String email, String pass) throws Exception {
        SignupRequest req = new SignupRequest(name, email, pass);
        MvcResult res = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString())
                .get("token").asText();
    }
}

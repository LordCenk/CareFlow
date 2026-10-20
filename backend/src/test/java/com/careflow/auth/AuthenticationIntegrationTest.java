package com.careflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully authenticate seeded admin and receive valid JWT")
    void shouldLoginAdminAndReceiveJwt() throws Exception {
        Map<String, String> loginPayload = Map.of(
                "email", "admin@careflow.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.email").value("admin@careflow.com"));
    }

    @Test
    @DisplayName("Should register new patient, login, and access /api/v1/users/me")
    void shouldRegisterPatientAndAccessProfile() throws Exception {
        String uniqueEmail = "patient.test." + System.currentTimeMillis() + "@careflow.com";
        Map<String, Object> registerPayload = Map.of(
                "name", "Test Patient",
                "email", uniqueEmail,
                "password", "password123",
                "contactNumber", "+1-555-9999",
                "address", "100 Test Blvd"
        );

        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(registerResponse).path("data").path("token").asText();

        // Access protected /api/v1/users/me with Bearer token
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(uniqueEmail))
                .andExpect(jsonPath("$.data.patientId").isNumber());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when accessing protected endpoint without token")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when patient attempts Admin-only department creation")
    void shouldReturn403WhenPatientAccessesAdminEndpoint() throws Exception {
        // Login as patient
        Map<String, String> loginPayload = Map.of(
                "email", "patient.alice@careflow.com",
                "password", "password123"
        );

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).path("data").path("token").asText();

        Map<String, String> deptPayload = Map.of(
                "name", "Unauthorized Dept",
                "description", "Should fail"
        );

        mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}

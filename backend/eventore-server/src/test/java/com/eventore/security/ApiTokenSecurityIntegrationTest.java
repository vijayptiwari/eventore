package com.eventore.security;

import com.eventore.EventoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EventoreApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTokenSecurityIntegrationTest {

    private static final String TOKEN = "integration-test-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configRequiresApiTokenWhenAuthEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void configAllowsValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void healthReadinessBypassesApiTokenFilter() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void diagnosticsRequiresApiToken() throws Exception {
        mockMvc.perform(get("/api/v1/diagnostics/subscriptions")).andExpect(status().isUnauthorized());
    }

    @Test
    void diagnosticsAllowsValidBearerToken() throws Exception {
        mockMvc.perform(
                        get("/api/v1/diagnostics/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void sseStreamRequiresApiToken() throws Exception {
        mockMvc.perform(get("/api/v1/stream/sub-test").param("connectionId", "conn-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webSocketHandshakeRejectedWithoutToken() throws Exception {
        mockMvc.perform(
                        get("/ws/stream")
                                .header("Upgrade", "websocket")
                                .header("Connection", "Upgrade")
                                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                                .header("Sec-WebSocket-Version", "13"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authMeRejectsForgedJwtInStaticTokenMode() throws Exception {
        long exp = java.time.Instant.now().plusSeconds(3600).getEpochSecond();
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"usr-jwt-1\",\"email\":\"jwt@acme.com\",\"roles\":[\"PLATFORM_ADMIN\"],\"workspaces\":[\"default\"],\"exp\":" + exp + "}").getBytes());
        String fakeSig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("sig".getBytes());
        String jwt = header + "." + payload + "." + fakeSig;

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workspacesEndpointReturnsWorkspaces() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").exists());
    }

    @Test
    void clusterStatusEndpointReturnsStatus() throws Exception {
        mockMvc.perform(get("/api/v1/cluster/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mode").value("LOCAL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.healthy").value(true));
    }
}

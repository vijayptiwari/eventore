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
}

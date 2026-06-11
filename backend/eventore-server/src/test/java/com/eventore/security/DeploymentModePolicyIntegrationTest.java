package com.eventore.security;

import com.eventore.EventoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EventoreApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "eventore.deployment-mode=READONLY",
            "eventore.security.api-token=integration-test-token"
        })
class DeploymentModePolicyIntegrationTest {

    private static final String TOKEN = "integration-test-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readonlyModeBlocksConnectionCreate() throws Exception {
        String body =
                """
                {"name":"blocked","protocol":"KAFKA","brokerUrl":"localhost:9092"}
                """;
        mockMvc.perform(
                        post("/api/v1/connections")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
    }
}

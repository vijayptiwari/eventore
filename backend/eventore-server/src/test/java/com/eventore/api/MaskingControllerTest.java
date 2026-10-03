package com.eventore.api;

import static org.junit.jupiter.api.Assertions.*;

import com.eventore.config.EventoreProperties;
import com.eventore.masking.MaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MaskingControllerTest {

    private MaskingController controller;

    @BeforeEach
    void setUp() {
        EventoreProperties properties = new EventoreProperties();
        MaskingService maskingService = new MaskingService(properties);
        controller = new MaskingController(maskingService);
    }

    @Test
    @DisplayName("GET /api/v1/masking/config returns active masking settings and default rules")
    void returnsActiveMaskingConfig() {
        ResponseEntity<MaskingController.MaskingConfigResponse> res = controller.getConfig();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        assertTrue(res.getBody().enabled());
        assertEquals("[REDACTED]", res.getBody().replacement());
        assertTrue(res.getBody().sensitiveFieldPatterns().contains("password"));
        assertTrue(res.getBody().sensitiveFieldPatterns().contains("creditcard"));
        assertTrue(res.getBody().sensitiveHeaderPatterns().contains("authorization"));
        assertTrue(res.getBody().maskValuesByPattern());
    }

    @Test
    @DisplayName("POST /api/v1/masking/preview redacts sensitive data and sets wasMasked=true")
    void previewRedactsSensitiveData() {
        String input = "{\"password\": \"secret123\", \"cardNumber\": \"4111-2222-3333-4444\", \"user\": \"bob\"}";
        var req = new MaskingController.MaskingPreviewRequest(input, "application/json");

        ResponseEntity<MaskingController.MaskingPreviewResponse> res = controller.preview(req);
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        assertTrue(res.getBody().wasMasked());
        assertEquals(input, res.getBody().original());
        assertFalse(res.getBody().masked().contains("secret123"));
        assertFalse(res.getBody().masked().contains("4111-2222-3333-4444"));
        assertTrue(res.getBody().masked().contains("\"password\":\"[REDACTED]\""));
        assertTrue(res.getBody().masked().contains("\"user\":\"bob\""));
    }

    @Test
    @DisplayName("POST /api/v1/masking/preview with non-sensitive data sets wasMasked=false")
    void previewNonSensitiveDataLeavesIntact() {
        String input = "{\"city\": \"Tokyo\", \"temperature\": 22}";
        var req = new MaskingController.MaskingPreviewRequest(input, "application/json");

        ResponseEntity<MaskingController.MaskingPreviewResponse> res = controller.preview(req);
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        assertFalse(res.getBody().wasMasked());
        assertEquals(input, res.getBody().masked());
    }
}

package com.eventore.masking;

import static org.junit.jupiter.api.Assertions.*;

import com.eventore.domain.UnifiedMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataMaskerTest {
    @org.junit.jupiter.api.Test
    void disablingHeaderMaskingDoesNotDisablePayloadMasking() {
        MaskingConfig config = MaskingConfig.defaultConfig();
        config.setMaskHeaders(false);
        org.assertj.core.api.Assertions.assertThat(DataMasker.maskHeaders(java.util.Map.of("authorization", "token"), config))
                .containsEntry("authorization", "token");
        org.assertj.core.api.Assertions.assertThat(DataMasker.maskPayload("{\"password\":\"secret\"}", "application/json", config))
                .contains("[REDACTED]").doesNotContain("secret");
    }

    @Test
    @DisplayName("Masks sensitive fields in nested JSON payloads")
    void masksNestedJsonSensitiveFields() {
        String json = """
        {
            "userId": "usr_12345",
            "password": "super-secret-password",
            "account": {
                "creditCard": "4111-2222-3333-4444",
                "balance": 1500.50,
                "api_key": "sec_abc123xyz"
            },
            "credentials": [
                {"token": "tok_999", "role": "admin"},
                {"token": "tok_888", "role": "user"}
            ]
        }
        """;

        UnifiedMessage msg = new UnifiedMessage();
        msg.setContentType("application/json");
        msg.setPayload(json);

        UnifiedMessage masked = DataMasker.mask(msg, MaskingConfig.defaultConfig());

        assertNotNull(masked);
        assertEquals("true", masked.getHeaders().get(DataMasker.HEADER_MASKED));
        String maskedPayload = masked.getPayload();

        assertFalse(maskedPayload.contains("super-secret-password"));
        assertFalse(maskedPayload.contains("4111-2222-3333-4444"));
        assertFalse(maskedPayload.contains("sec_abc123xyz"));
        assertFalse(maskedPayload.contains("tok_999"));
        assertFalse(maskedPayload.contains("tok_888"));

        assertTrue(maskedPayload.contains("\"password\":\"[REDACTED]\""));
        assertTrue(maskedPayload.contains("\"creditCard\":\"[REDACTED]\""));
        assertTrue(maskedPayload.contains("\"api_key\":\"[REDACTED]\""));
        assertTrue(maskedPayload.contains("usr_12345"));
        assertTrue(maskedPayload.contains("1500.5"));
    }

    @Test
    @DisplayName("Masks value patterns such as emails and SSNs inside string fields")
    void masksValuePatternsInStrings() {
        String json = """
        {
            "description": "Customer contact john.doe@company.org with SSN 123-45-6789 reported card 5500-0000-0000-1111"
        }
        """;

        UnifiedMessage msg = new UnifiedMessage();
        msg.setContentType("application/json");
        msg.setPayload(json);

        UnifiedMessage masked = DataMasker.mask(msg, MaskingConfig.defaultConfig());
        String payload = masked.getPayload();

        assertFalse(payload.contains("john.doe@company.org"));
        assertFalse(payload.contains("123-45-6789"));
        assertFalse(payload.contains("5500-0000-0000-1111"));

        assertTrue(payload.contains("[EMAIL_REDACTED]"));
        assertTrue(payload.contains("[SSN_REDACTED]"));
        assertTrue(payload.contains("[CARD_REDACTED]"));
    }

    @Test
    @DisplayName("Masks sensitive headers like Authorization and Cookie")
    void masksSensitiveHeaders() {
        UnifiedMessage msg = new UnifiedMessage();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.secret");
        headers.put("Cookie", "session=s3cr3t123");
        headers.put("X-Custom-TraceId", "trace-789");
        msg.setHeaders(headers);
        msg.setPayload("ok");

        UnifiedMessage masked = DataMasker.mask(msg, MaskingConfig.defaultConfig());

        assertEquals("[REDACTED]", masked.getHeaders().get("Authorization"));
        assertEquals("[REDACTED]", masked.getHeaders().get("Cookie"));
        assertEquals("trace-789", masked.getHeaders().get("X-Custom-TraceId"));
        assertEquals("true", masked.getHeaders().get(DataMasker.HEADER_MASKED));
    }

    @Test
    @DisplayName("Masks plain text key-value pairs and PII patterns")
    void masksPlainTextKeyValueAndPii() {
        String plain = "auth: password=mypassword, contact=support@eventore.io, nationalId=999-88-7777";
        UnifiedMessage msg = new UnifiedMessage();
        msg.setContentType("text/plain");
        msg.setPayload(plain);

        UnifiedMessage masked = DataMasker.mask(msg, MaskingConfig.defaultConfig());
        String result = masked.getPayload();

        assertFalse(result.contains("mypassword"));
        assertFalse(result.contains("support@eventore.io"));
        assertFalse(result.contains("999-88-7777"));

        assertTrue(result.contains("password=[REDACTED]"));
        assertTrue(result.contains("[EMAIL_REDACTED]"));
        assertTrue(result.contains("[SSN_REDACTED]"));
    }

    @Test
    @DisplayName("Masks Base64 encoded UTF-8 payloads")
    void masksBase64Payload() {
        String rawJson = "{\"secret\": \"classified-key-999\"}";
        String b64 = Base64.getEncoder().encodeToString(rawJson.getBytes(StandardCharsets.UTF_8));

        UnifiedMessage msg = new UnifiedMessage();
        msg.setContentType("application/base64");
        msg.setPayload(b64);

        UnifiedMessage masked = DataMasker.mask(msg, MaskingConfig.defaultConfig());

        byte[] decoded = Base64.getDecoder().decode(masked.getPayload());
        String decodedStr = new String(decoded, StandardCharsets.UTF_8);

        assertFalse(decodedStr.contains("classified-key-999"));
        assertTrue(decodedStr.contains("\"secret\":\"[REDACTED]\""));
    }

    @Test
    @DisplayName("Passes through without modification when masking is disabled")
    void passesThroughWhenDisabled() {
        String json = "{\"password\": \"keep-intact\"}";
        UnifiedMessage msg = new UnifiedMessage();
        msg.setPayload(json);

        MaskingConfig config = MaskingConfig.disabled();
        UnifiedMessage masked = DataMasker.mask(msg, config);

        assertEquals(msg, masked);
        assertEquals(json, masked.getPayload());
        assertNull(masked.getHeaders().get(DataMasker.HEADER_MASKED));
    }

    @Test
    @DisplayName("Supports custom replacement token and custom sensitive fields")
    void customReplacementAndFields() {
        MaskingConfig config = new MaskingConfig();
        config.setReplacement("***CONFIDENTIAL***");
        config.addSensitiveFieldPattern("internalTaxNumber");

        String json = "{\"internalTaxNumber\": \"TX-987654\", \"username\": \"alice\"}";
        UnifiedMessage msg = new UnifiedMessage();
        msg.setPayload(json);

        UnifiedMessage masked = DataMasker.mask(msg, config);
        String result = masked.getPayload();

        assertFalse(result.contains("TX-987654"));
        assertTrue(result.contains("***CONFIDENTIAL***"));
        assertTrue(result.contains("alice"));
    }
}

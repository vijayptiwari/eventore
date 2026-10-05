package com.eventore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String SECRET = "my-super-secret-jwt-signing-key-32-bytes!!";
    private JwtTokenValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        validator = new JwtTokenValidator(mapper);
    }

    private String createSignedJwt(String payloadJson, String secret) throws Exception {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII));
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        return headerB64 + "." + payloadB64 + "." + sigB64;
    }

    @Test
    void isJwtFormatReturnsTrueOnlyFor3DotSeparatedSegments() {
        assertThat(validator.isJwtFormat("a.b.c")).isTrue();
        assertThat(validator.isJwtFormat("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature")).isTrue();
        assertThat(validator.isJwtFormat("not-a-jwt")).isFalse();
        assertThat(validator.isJwtFormat("static-token")).isFalse();
        assertThat(validator.isJwtFormat(null)).isFalse();
    }

    @Test
    void validatesValidSignedJwtAndExtractsClaims() throws Exception {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = """
            {
              "sub": "usr_9988",
              "email": "alice@company.com",
              "name": "Alice Enterprise",
              "roles": ["WORKSPACE_ADMIN", "OPERATOR"],
              "workspaces": ["payments", "analytics"],
              "iss": "https://auth.company.com",
              "aud": "eventore-api",
              "exp": %d
            }
            """.formatted(exp);

        String jwt = createSignedJwt(payload, SECRET);

        UserPrincipal principal = validator.validateToken(jwt, SECRET, "https://auth.company.com", "eventore-api");
        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo("usr_9988");
        assertThat(principal.email()).isEqualTo("alice@company.com");
        assertThat(principal.displayName()).isEqualTo("Alice Enterprise");
        assertThat(principal.roles()).contains(UserRole.WORKSPACE_ADMIN, UserRole.OPERATOR);
        assertThat(principal.assignedWorkspaces()).contains("payments", "analytics");
        assertThat(principal.isJwt()).isTrue();
        assertThat(principal.canAccessWorkspace("payments")).isTrue();
        assertThat(principal.canAccessWorkspace("unknown-tenant")).isFalse();
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        long pastExp = Instant.now().minusSeconds(120).getEpochSecond();
        String payload = """
            {
              "sub": "usr_expired",
              "exp": %d
            }
            """.formatted(pastExp);

        String jwt = createSignedJwt(payload, SECRET);

        assertThatThrownBy(() -> validator.validateToken(jwt, SECRET, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = "{\"sub\":\"usr_tamper\",\"exp\":" + exp + "}";
        String jwt = createSignedJwt(payload, "different-wrong-secret-key-12345");

        assertThatThrownBy(() -> validator.validateToken(jwt, SECRET, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void rejectsIssuerMismatch() throws Exception {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = "{\"sub\":\"usr_iss\",\"iss\":\"https://other.com\",\"exp\":" + exp + "}";
        String jwt = createSignedJwt(payload, SECRET);

        assertThatThrownBy(() -> validator.validateToken(jwt, SECRET, "https://expected.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer mismatch");
    }
}

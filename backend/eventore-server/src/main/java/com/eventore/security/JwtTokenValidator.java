package com.eventore.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates OpenID Connect / OAuth2 JWT bearer tokens and extracts identities,
 * roles, and multi-tenant workspace claims (REQ-103).
 */
@Component
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);
    private final ObjectMapper objectMapper;

    public JwtTokenValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public boolean isJwtFormat(String token) {
        if (token == null) {
            return false;
        }
        int firstDot = token.indexOf('.');
        int secondDot = firstDot > 0 ? token.indexOf('.', firstDot + 1) : -1;
        return firstDot > 0 && secondDot > firstDot && secondDot < token.length() - 1;
    }

    public UserPrincipal validateToken(
            String token,
            String secretKey,
            String expectedIssuer,
            String expectedAudience) {
        if (!isJwtFormat(token)) {
            throw new IllegalArgumentException("Token is not in valid 3-part JWT format");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT structure; expected 3 segments");
        }

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("JWT signing secret is required");
        }
        try {
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) {
                throw new IllegalArgumentException("Only HS256 JWT signatures are supported");
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid JWT header", e);
        }
        verifySignature(parts[0], parts[1], parts[2], secretKey);

        // 2. Decode payload JSON
        JsonNode payload;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            payload = objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode JWT payload: " + e.getMessage(), e);
        }

        long nowEpochSec = Instant.now().getEpochSecond();

        // 3. Expiration validation (with 60-second clock skew allowance)
        if (!payload.path("exp").isIntegralNumber() || payload.path("exp").asLong() <= 0) {
            throw new IllegalArgumentException("JWT expiration is required");
        }
        if (payload.has("exp")) {
            long exp = payload.get("exp").asLong();
            if (exp > 0 && exp + 60 < nowEpochSec) {
                throw new IllegalArgumentException("JWT token has expired at epoch " + exp);
            }
        }

        // 4. Not-before validation
        if (payload.has("nbf")) {
            long nbf = payload.get("nbf").asLong();
            if (nbf > nowEpochSec + 60) {
                throw new IllegalArgumentException("JWT token not valid before epoch " + nbf);
            }
        }

        // 5. Issuer check
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            String iss = payload.path("iss").asText();
            if (!expectedIssuer.equals(iss)) {
                throw new IllegalArgumentException("JWT issuer mismatch: expected '" + expectedIssuer + "', got '" + iss + "'");
            }
        }

        // 6. Audience check
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            JsonNode audNode = payload.path("aud");
            boolean audMatch = false;
            if (audNode.isArray()) {
                for (JsonNode item : audNode) {
                    if (expectedAudience.equals(item.asText())) {
                        audMatch = true;
                        break;
                    }
                }
            } else if (expectedAudience.equals(audNode.asText())) {
                audMatch = true;
            }
            if (!audMatch) {
                throw new IllegalArgumentException("JWT audience mismatch for " + expectedAudience);
            }
        }

        // 7. Extract identities
        String userId = payload.has("sub") ? payload.get("sub").asText() : "jwt-user";
        String email = payload.has("email") ? payload.get("email").asText() : userId + "@token.jwt";
        String displayName = payload.has("name")
                ? payload.get("name").asText()
                : payload.has("preferred_username")
                        ? payload.get("preferred_username").asText()
                        : userId;

        // 8. Extract roles
        Set<UserRole> roles = extractRoles(payload);

        // 9. Extract workspaces
        Set<String> workspaces = extractWorkspaces(payload);
        String currentWorkspace = workspaces.contains("default") ? "default" : workspaces.iterator().next();

        return new UserPrincipal(
                userId,
                email,
                displayName,
                roles,
                workspaces,
                currentWorkspace,
                true);
    }

    private void verifySignature(String headerB64, String payloadB64, String sigB64, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            String signingInput = headerB64 + "." + payloadB64;
            byte[] expectedSig = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] providedSig = Base64.getUrlDecoder().decode(sigB64);

            if (!MessageDigest.isEqual(expectedSig, providedSig)) {
                throw new IllegalArgumentException("JWT HMAC-SHA256 signature verification failed");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cryptographic error verifying JWT signature: " + e.getMessage(), e);
        }
    }

    private Set<UserRole> extractRoles(JsonNode payload) {
        Set<UserRole> roles = new HashSet<>();

        // Check "roles"
        collectRolesFromNode(payload.get("roles"), roles);
        // Check "role"
        collectRolesFromNode(payload.get("role"), roles);
        // Check "groups"
        collectRolesFromNode(payload.get("groups"), roles);
        // Check "realm_access.roles" (Keycloak)
        if (payload.has("realm_access") && payload.get("realm_access").has("roles")) {
            collectRolesFromNode(payload.get("realm_access").get("roles"), roles);
        }
        // Check "scope"
        if (payload.has("scope")) {
            String scope = payload.get("scope").asText();
            for (String s : scope.split("\\s+")) {
                UserRole role = parseRoleString(s);
                if (role != null) {
                    roles.add(role);
                }
            }
        }

        if (roles.isEmpty()) {
            roles.add(UserRole.VIEWER);
        }
        return roles;
    }

    private void collectRolesFromNode(JsonNode node, Set<UserRole> roles) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                UserRole r = parseRoleString(item.asText());
                if (r != null) {
                    roles.add(r);
                }
            }
        } else if (node.isTextual()) {
            UserRole r = parseRoleString(node.asText());
            if (r != null) {
                roles.add(r);
            }
        }
    }

    private UserRole parseRoleString(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        if (clean.contains("platform_admin") || clean.contains("superadmin") || clean.equals("admin")) {
            return UserRole.PLATFORM_ADMIN;
        }
        if (clean.contains("workspace_admin")) {
            return UserRole.WORKSPACE_ADMIN;
        }
        if (clean.contains("operator") || clean.contains("publisher")) {
            return UserRole.OPERATOR;
        }
        if (clean.contains("viewer") || clean.contains("reader") || clean.contains("user")) {
            return UserRole.VIEWER;
        }
        return null;
    }

    private Set<String> extractWorkspaces(JsonNode payload) {
        Set<String> workspaces = new HashSet<>();
        if (payload.has("workspaces") && payload.get("workspaces").isArray()) {
            for (JsonNode item : payload.get("workspaces")) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    workspaces.add(item.asText().trim());
                }
            }
        }
        if (payload.has("workspace") && payload.get("workspace").isTextual()) {
            workspaces.add(payload.get("workspace").asText().trim());
        }
        if (payload.has("tenant_id") && payload.get("tenant_id").isTextual()) {
            workspaces.add(payload.get("tenant_id").asText().trim());
        }
        if (workspaces.isEmpty()) {
            workspaces.add("default");
        }
        return workspaces;
    }
}

package com.eventore.masking;

import com.eventore.domain.UnifiedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, thread-safe field-level data masking and PII redaction engine.
 * Redacts sensitive fields, credentials, and PII patterns in JSON structures, headers, and plain text.
 */
public final class DataMasker {

    public static final String HEADER_MASKED = "x-eventore-masked";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Regex for 13-19 digit credit card numbers (grouped or continuous)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[- ]){3}\\d{4}\\b|\\b(?:\\d{4}[- ]){2}\\d{4}[- ]\\d{1,4}\\b|\\b\\d{15,19}\\b");

    // Regex for US Social Security Numbers (XXX-XX-XXXX)
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    // Regex for Email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    // Regex for key-value plain text redaction (e.g. password=secret or token: "123")
    private static final Pattern PLAIN_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(password|secret|token|apikey|api_key|access_token|authorization|credential)[\\s]*[:=][\\s]*([\"']?)([^\"'\\r\\n,;]+)([\"']?)");

    private DataMasker() {}

    /**
     * Masks sensitive headers, fields, and PII within a {@link UnifiedMessage}.
     * Returns a new sanitized instance with {@code x-eventore-masked: true}.
     */
    public static UnifiedMessage mask(UnifiedMessage original, MaskingConfig config) {
        if (original == null) {
            return null;
        }
        if (config == null || !config.isEnabled()) {
            return original;
        }

        UnifiedMessage masked = new UnifiedMessage();
        masked.setId(original.getId());
        masked.setDestination(original.getDestination());
        masked.setTimestamp(original.getTimestamp());
        masked.setProtocol(original.getProtocol());
        masked.setDirection(original.getDirection());
        masked.setConnectionId(original.getConnectionId());
        masked.setContentType(original.getContentType());

        // Mask headers
        Map<String, String> maskedHeaders = maskHeaders(original.getHeaders(), config);
        maskedHeaders.put(HEADER_MASKED, "true");
        masked.setHeaders(maskedHeaders);

        // Mask payload
        String maskedPayload = maskPayload(original.getPayload(), original.getContentType(), config);
        masked.setPayload(maskedPayload);

        return masked;
    }

    /**
     * Masks headers matching sensitive header patterns.
     */
    public static Map<String, String> maskHeaders(Map<String, String> headers, MaskingConfig config) {
        if (headers == null || headers.isEmpty() || config == null || !config.isEnabled() || !config.isMaskHeaders()) {
            return headers != null ? new HashMap<>(headers) : new HashMap<>();
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (config.isSensitiveHeader(key)) {
                result.put(key, config.getReplacement());
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    public record MaskResult(String maskedPayload, int redactionCount) {
        public boolean isRedacted() {
            return redactionCount > 0;
        }
    }

    /**
     * Sanitizes a payload based on content type, JSON structure, and PII value patterns,
     * returning both the sanitized payload and the count of redactions performed.
     */
    public static MaskResult maskPayloadWithStats(String payload, String contentType, MaskingConfig config) {
        if (payload == null || payload.isBlank() || config == null || !config.isEnabled()) {
            return new MaskResult(payload, 0);
        }

        // Check for Base64 payload
        if (contentType != null && contentType.toLowerCase().contains("base64")) {
            return maskBase64PayloadWithStats(payload, config);
        }

        String trimmed = payload.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(trimmed);
                int count = maskJsonNode(root, config);
                if (count == 0) {
                    return new MaskResult(payload, 0);
                }
                return new MaskResult(OBJECT_MAPPER.writeValueAsString(root), count);
            } catch (JsonProcessingException e) {
                // Not valid JSON, fall back to plain text masking
            }
        }

        return maskPlainTextWithStats(payload, config);
    }

    /**
     * Sanitizes a payload based on content type, JSON structure, and PII value patterns.
     */
    public static String maskPayload(String payload, String contentType, MaskingConfig config) {
        return maskPayloadWithStats(payload, contentType, config).maskedPayload();
    }

    /**
     * Recursively traverses and masks JSON trees, returning the count of redactions performed.
     */
    private static int maskJsonNode(JsonNode node, MaskingConfig config) {
        if (node == null || node.isNull()) {
            return 0;
        }

        int count = 0;
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();

                if (config.isSensitiveField(fieldName)) {
                    objectNode.set(fieldName, TextNode.valueOf(config.getReplacement()));
                    count++;
                } else if (child.isObject() || child.isArray()) {
                    count += maskJsonNode(child, config);
                } else if (child.isTextual() && config.isMaskValuesByPattern()) {
                    String maskedText = maskValuePatterns(child.asText(), config);
                    if (!maskedText.equals(child.asText())) {
                        objectNode.set(fieldName, TextNode.valueOf(maskedText));
                        count++;
                    }
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child.isObject() || child.isArray()) {
                    count += maskJsonNode(child, config);
                } else if (child.isTextual() && config.isMaskValuesByPattern()) {
                    String maskedText = maskValuePatterns(child.asText(), config);
                    if (!maskedText.equals(child.asText())) {
                        arrayNode.set(i, TextNode.valueOf(maskedText));
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Masks sensitive PII patterns in string values (Credit cards, SSNs, Emails).
     */
    public static String maskValuePatterns(String text, MaskingConfig config) {
        if (text == null || text.isBlank() || config == null || !config.isMaskValuesByPattern()) {
            return text;
        }

        String result = text;

        if (config.isMaskCreditCards()) {
            result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[CARD_REDACTED]");
        }

        if (config.isMaskSsns()) {
            result = SSN_PATTERN.matcher(result).replaceAll("[SSN_REDACTED]");
        }

        if (config.isMaskEmails()) {
            result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL_REDACTED]");
        }

        return result;
    }

    /**
     * Sanitizes plain text (non-JSON) payloads by redacting key-value secrets and value PII.
     */
    public static String maskPlainText(String text, MaskingConfig config) {
        return maskPlainTextWithStats(text, config).maskedPayload();
    }

    /**
     * Sanitizes plain text (non-JSON) payloads by redacting key-value secrets and value PII with stats.
     */
    public static MaskResult maskPlainTextWithStats(String text, MaskingConfig config) {
        if (text == null || text.isBlank() || config == null || !config.isEnabled()) {
            return new MaskResult(text, 0);
        }

        int count = 0;
        String result = text;

        // Mask key=value or key: value pairs
        Matcher kvMatcher = PLAIN_KEY_VALUE_PATTERN.matcher(result);
        if (kvMatcher.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                count++;
                String key = kvMatcher.group(1);
                String openQuote = kvMatcher.group(2);
                String closeQuote = kvMatcher.group(4);
                String replacement = key + "=" + openQuote + config.getReplacement() + closeQuote;
                kvMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } while (kvMatcher.find());
            kvMatcher.appendTail(sb);
            result = sb.toString();
        }

        if (config.isMaskValuesByPattern()) {
            String beforeVal = result;
            result = maskValuePatterns(result, config);
            if (!beforeVal.equals(result)) {
                count++;
            }
        }

        return new MaskResult(result, count);
    }

    /**
     * Decodes Base64 payload, masks UTF-8 content if valid, and re-encodes to Base64 with stats.
     */
    private static MaskResult maskBase64PayloadWithStats(String base64Payload, MaskingConfig config) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Payload.trim());
            String decodedString = new String(decoded, StandardCharsets.UTF_8);
            MaskResult inner = maskPayloadWithStats(decodedString, "text/plain", config);
            String encoded = Base64.getEncoder().encodeToString(inner.maskedPayload().getBytes(StandardCharsets.UTF_8));
            return new MaskResult(encoded, inner.redactionCount());
        } catch (Exception e) {
            // Not base64 or decoding error; return as is
            return new MaskResult(base64Payload, 0);
        }
    }
}

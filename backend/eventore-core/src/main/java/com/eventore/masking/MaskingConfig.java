package com.eventore.masking;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration options and sensitive pattern rules for field-level data masking and PII redaction.
 */
public class MaskingConfig {

    public static final String DEFAULT_REPLACEMENT = "[REDACTED]";

    public static final Set<String> DEFAULT_SENSITIVE_FIELDS = Set.of(
            "password", "secret", "token", "apikey", "api_key", "access_token",
            "refreshtoken", "refresh_token", "authorization", "privatekey", "private_key",
            "credential", "credentials", "creditcard", "credit_card", "cardnumber",
            "card_number", "cvv", "cvc", "pan", "ssn", "socialsecuritynumber",
            "social_security_number", "nationalid", "national_id"
    );

    public static final Set<String> DEFAULT_SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "x-api-key", "cookie", "set-cookie",
            "token", "secret"
    );

    private boolean enabled = true;
    private String replacement = DEFAULT_REPLACEMENT;
    private Set<String> sensitiveFieldPatterns = new HashSet<>(DEFAULT_SENSITIVE_FIELDS);
    private Set<String> sensitiveHeaderPatterns = new HashSet<>(DEFAULT_SENSITIVE_HEADERS);
    private boolean maskValuesByPattern = true;
    private boolean maskHeaders = true;

    public boolean isMaskHeaders() {
        return maskHeaders;
    }

    public void setMaskHeaders(boolean maskHeaders) {
        this.maskHeaders = maskHeaders;
    }
    private boolean maskEmails = true;
    private boolean maskCreditCards = true;
    private boolean maskSsns = true;

    public MaskingConfig() {}

    public static MaskingConfig defaultConfig() {
        return new MaskingConfig();
    }

    public static MaskingConfig disabled() {
        MaskingConfig config = new MaskingConfig();
        config.setEnabled(false);
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement != null ? replacement : DEFAULT_REPLACEMENT;
    }

    public Set<String> getSensitiveFieldPatterns() {
        return Collections.unmodifiableSet(sensitiveFieldPatterns);
    }

    public void setSensitiveFieldPatterns(Collection<String> patterns) {
        this.sensitiveFieldPatterns = new HashSet<>();
        if (patterns != null) {
            for (String p : patterns) {
                if (p != null && !p.isBlank()) {
                    this.sensitiveFieldPatterns.add(normalize(p));
                }
            }
        }
    }

    public void addSensitiveFieldPattern(String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            this.sensitiveFieldPatterns.add(normalize(pattern));
        }
    }

    public Set<String> getSensitiveHeaderPatterns() {
        return Collections.unmodifiableSet(sensitiveHeaderPatterns);
    }

    public void setSensitiveHeaderPatterns(Collection<String> patterns) {
        this.sensitiveHeaderPatterns = new HashSet<>();
        if (patterns != null) {
            for (String p : patterns) {
                if (p != null && !p.isBlank()) {
                    this.sensitiveHeaderPatterns.add(normalize(p));
                }
            }
        }
    }

    public boolean isMaskValuesByPattern() {
        return maskValuesByPattern;
    }

    public void setMaskValuesByPattern(boolean maskValuesByPattern) {
        this.maskValuesByPattern = maskValuesByPattern;
    }

    public boolean isMaskEmails() {
        return maskEmails;
    }

    public void setMaskEmails(boolean maskEmails) {
        this.maskEmails = maskEmails;
    }

    public boolean isMaskCreditCards() {
        return maskCreditCards;
    }

    public void setMaskCreditCards(boolean maskCreditCards) {
        this.maskCreditCards = maskCreditCards;
    }

    public boolean isMaskSsns() {
        return maskSsns;
    }

    public void setMaskSsns(boolean maskSsns) {
        this.maskSsns = maskSsns;
    }

    /**
     * Normalizes a field or header key for consistent comparison (lowercase, trimmed).
     */
    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks if a field name matches any registered sensitive pattern (exact match or contains).
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String norm = normalize(fieldName);
        String stripped = norm.replaceAll("[_\\-.]", "");

        for (String pattern : sensitiveFieldPatterns) {
            String normPattern = normalize(pattern);
            String strippedPattern = normPattern.replaceAll("[_\\-.]", "");
            if (norm.equals(normPattern) || stripped.equals(strippedPattern) ||
                norm.endsWith("." + normPattern) || norm.contains(normPattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a header name matches any registered sensitive header pattern.
     */
    public boolean isSensitiveHeader(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            return false;
        }
        String norm = normalize(headerName);
        for (String pattern : sensitiveHeaderPatterns) {
            if (norm.equals(normalize(pattern)) || norm.contains(normalize(pattern))) {
                return true;
            }
        }
        return false;
    }
}

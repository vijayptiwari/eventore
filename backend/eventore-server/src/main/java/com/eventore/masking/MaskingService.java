package com.eventore.masking;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.UnifiedMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating field-level data masking and PII redaction across streaming and inspection pipelines.
 */
@Service
public class MaskingService {

    private final EventoreProperties properties;

    public MaskingService(EventoreProperties properties) {
        this.properties = properties != null ? properties : new EventoreProperties();
    }

    /**
     * Resolves the active masking configuration based on application properties.
     */
    public MaskingConfig getConfig() {
        if (properties.getMasking() != null) {
            return properties.getMasking().toConfig();
        }
        return MaskingConfig.defaultConfig();
    }

    /**
     * Checks if masking is globally enabled.
     */
    public boolean isMaskingEnabled() {
        return getConfig().isEnabled();
    }

    /**
     * Masks a single message using active server configuration.
     */
    public UnifiedMessage mask(UnifiedMessage message) {
        return DataMasker.mask(message, getConfig());
    }

    /**
     * Masks a message unless explicitly overridden with {@code overrideMask = false}.
     */
    public UnifiedMessage mask(UnifiedMessage message, Boolean overrideMask) {
        if (Boolean.FALSE.equals(overrideMask)) {
            return message;
        }
        return mask(message);
    }

    /**
     * Masks a list of messages.
     */
    public List<UnifiedMessage> mask(List<UnifiedMessage> messages, Boolean overrideMask) {
        if (messages == null || messages.isEmpty() || Boolean.FALSE.equals(overrideMask)) {
            return messages;
        }
        List<UnifiedMessage> result = new ArrayList<>(messages.size());
        MaskingConfig config = getConfig();
        for (UnifiedMessage m : messages) {
            result.add(DataMasker.mask(m, config));
        }
        return result;
    }

    /**
     * Sanitizes a sample payload string for UI or MCP preview.
     */
    public String maskPayloadPreview(String payload, String contentType) {
        return DataMasker.maskPayload(payload, contentType, getConfig());
    }

    /**
     * Sanitizes a sample payload string for UI or MCP preview with redaction statistics.
     */
    public DataMasker.MaskResult maskPayloadWithStats(String payload, String contentType) {
        return DataMasker.maskPayloadWithStats(payload, contentType, getConfig());
    }
}

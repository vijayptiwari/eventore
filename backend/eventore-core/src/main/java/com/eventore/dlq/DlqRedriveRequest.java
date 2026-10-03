package com.eventore.dlq;

import java.util.List;
import java.util.Map;

/**
 * Request payload for redriving dead-lettered messages back to a primary destination.
 */
public record DlqRedriveRequest(
        String sourceTopic,
        String targetTopic,
        List<String> messageIds,
        String editedPayload,
        Map<String, String> editedHeaders,
        Integer maxMessages) {}

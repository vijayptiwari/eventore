package com.eventore.dlq;

import com.eventore.domain.UnifiedMessage;
import java.util.Map;

/**
 * Enriched representation of a dead-lettered message with extracted error diagnostics.
 */
public record DlqMessageInfo(
        UnifiedMessage message,
        String originalTopic,
        String failureReason,
        String exceptionClass,
        String stackTraceSnippet) {

    public static DlqMessageInfo from(UnifiedMessage message) {
        if (message == null) {
            return null;
        }
        Map<String, String> headers = message.getHeaders();
        String originalTopic = null;
        String reason = null;
        String exClass = null;
        String stackTrace = null;

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String k = entry.getKey().toLowerCase();
                String v = entry.getValue();
                if (v == null || v.isBlank()) {
                    continue;
                }
                if (k.contains("original-topic") || k.contains("original_topic") || k.contains("dlt-original-topic")) {
                    originalTopic = v;
                } else if (k.contains("exception-message") || k.contains("exception_message")
                        || k.contains("error-message") || k.contains("deadletter-reason")
                        || k.contains("dlt-exception-message")) {
                    reason = v;
                } else if (k.contains("exception-fqcn") || k.contains("exception-class")
                        || k.contains("exception_class") || k.contains("dlt-exception-fqcn")) {
                    exClass = v;
                } else if (k.contains("exception-stacktrace") || k.contains("stacktrace")
                        || k.contains("dlt-exception-stacktrace")) {
                    stackTrace = v.length() > 500 ? v.substring(0, 500) + "..." : v;
                }
            }
        }
        return new DlqMessageInfo(message, originalTopic, reason, exClass, stackTrace);
    }
}

package com.eventore.dlq;

import java.time.Instant;
import java.util.List;

/**
 * Result of a DLQ redrive operation.
 */
public record DlqRedriveResult(
        String sourceTopic,
        String targetTopic,
        int redrivenCount,
        int failedCount,
        List<String> errors,
        String timestamp) {

    public static DlqRedriveResult success(String sourceTopic, String targetTopic, int count) {
        return new DlqRedriveResult(sourceTopic, targetTopic, count, 0, List.of(), Instant.now().toString());
    }

    public static DlqRedriveResult partial(
            String sourceTopic, String targetTopic, int count, int failed, List<String> errors) {
        return new DlqRedriveResult(
                sourceTopic, targetTopic, count, failed, errors != null ? errors : List.of(), Instant.now().toString());
    }

    public static DlqRedriveResult failure(String sourceTopic, String targetTopic, String error) {
        return new DlqRedriveResult(
                sourceTopic, targetTopic, 0, 1, List.of(error), Instant.now().toString());
    }
}

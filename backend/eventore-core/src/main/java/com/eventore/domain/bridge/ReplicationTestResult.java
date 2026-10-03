package com.eventore.domain.bridge;

import java.util.Map;

/**
 * Result of a dry-run test of replication bridge rules (REQ-110).
 */
public record ReplicationTestResult(
    boolean passedFilter,
    boolean loopDetected,
    Map<String, String> transformedHeaders,
    String transformedPayload,
    String filterReason
) {}

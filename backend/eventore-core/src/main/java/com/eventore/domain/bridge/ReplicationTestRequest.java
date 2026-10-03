package com.eventore.domain.bridge;

import java.util.Map;

/**
 * Request for dry-run testing replication bridge transform & filter rules (REQ-110).
 */
public record ReplicationTestRequest(
    Map<String, String> headers,
    String payload,
    Map<String, String> headerTransform,
    String payloadFilter,
    Boolean loopPrevention,
    String bridgeId
) {}

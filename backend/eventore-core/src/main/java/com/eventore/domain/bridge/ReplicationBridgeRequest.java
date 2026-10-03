package com.eventore.domain.bridge;

import java.util.Map;

/**
 * Request payload for creating or updating a replication bridge (REQ-110).
 */
public record ReplicationBridgeRequest(
    String name,
    String sourceConnectionId,
    String sourceDestination,
    String targetConnectionId,
    String targetDestination,
    Map<String, String> headerTransform,
    String payloadFilter,
    Boolean loopPrevention,
    Boolean autoStart
) {}

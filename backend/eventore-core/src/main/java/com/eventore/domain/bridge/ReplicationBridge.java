package com.eventore.domain.bridge;

import java.util.Map;

/**
 * Cross-broker data replication pipeline definition (REQ-110).
 */
public record ReplicationBridge(
    String id,
    String name,
    String sourceConnectionId,
    String sourceDestination,
    String targetConnectionId,
    String targetDestination,
    Map<String, String> headerTransform,
    String payloadFilter,
    boolean loopPrevention,
    boolean enabled,
    String createdAt,
    ReplicationBridgeStats stats
) {}

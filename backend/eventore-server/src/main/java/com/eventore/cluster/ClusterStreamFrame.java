package com.eventore.cluster;

import com.eventore.domain.UnifiedMessage;
import java.util.UUID;

/**
 * Encapsulates a streaming message or lifecycle notification distributed across
 * multi-replica EventOre pods over the cluster subscription bus (REQ-102 / Pattern C).
 */
public record ClusterStreamFrame(
        String frameId,
        String subscriptionId,
        String originNodeId,
        String eventType,
        UnifiedMessage message,
        String detail,
        long timestampEpochMs) {

    public static ClusterStreamFrame of(
            String subscriptionId,
            String originNodeId,
            String eventType,
            UnifiedMessage message,
            String detail) {
        return new ClusterStreamFrame(
                UUID.randomUUID().toString(),
                subscriptionId,
                originNodeId,
                eventType,
                message,
                detail,
                System.currentTimeMillis());
    }
}

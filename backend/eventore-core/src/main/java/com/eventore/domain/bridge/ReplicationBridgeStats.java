package com.eventore.domain.bridge;

/**
 * Live metrics and throughput statistics for a replication bridge (REQ-110).
 */
public record ReplicationBridgeStats(
    long totalReplicated,
    long bytesReplicated,
    long errorsCount,
    String lastReplicatedAt,
    String lastError,
    ReplicationBridgeState state
) {
    public static ReplicationBridgeStats initial() {
        return new ReplicationBridgeStats(0L, 0L, 0L, null, null, ReplicationBridgeState.STOPPED);
    }
}

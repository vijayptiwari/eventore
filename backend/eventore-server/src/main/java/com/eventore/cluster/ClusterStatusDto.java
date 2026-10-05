package com.eventore.cluster;

/**
 * Cluster bus operational status and metrics for distributed streaming nodes (REQ-102).
 */
public record ClusterStatusDto(
        String mode,
        String nodeId,
        int activeSubscribers,
        long totalBroadcasts,
        long totalReceived,
        int connectedPeers,
        boolean healthy) {}

package com.eventore.domain.bridge;

/**
 * Execution state of a cross-broker replication bridge (REQ-110).
 */
public enum ReplicationBridgeState {
    RUNNING,
    STOPPED,
    ERROR,
    PAUSED
}

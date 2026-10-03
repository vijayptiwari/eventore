package com.eventore.domain;

/**
 * Supported connection profile persistence store types (REQ-101).
 */
public enum ConnectionStoreType {
    /** Local JSON file persistence (default: /data/connections.json). */
    FILE,

    /** Relational database persistence via JDBC with distributed optimistic locking. */
    JDBC,

    /** Kubernetes Custom Resource Definition (CRD) declarative persistence. */
    K8S_CRD,

    /** Ephemeral in-memory store for stateless instances or testing. */
    IN_MEMORY
}

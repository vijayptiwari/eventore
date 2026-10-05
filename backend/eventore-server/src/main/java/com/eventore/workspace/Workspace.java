package com.eventore.workspace;

import java.time.Instant;
import java.util.Set;

/**
 * Multi-tenant workspace domain model for scoped connection and destination isolation (REQ-104).
 */
public record Workspace(
        String id,
        String name,
        String description,
        Instant createdAt,
        Set<String> tags) {

    public static Workspace of(String id, String name, String description, String... tags) {
        return new Workspace(
                id,
                name,
                description,
                Instant.now(),
                tags != null ? Set.of(tags) : Set.of());
    }
}

package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import java.util.Map;
import java.util.Optional;

/**
 * Pluggable SPI for persisting and loading connection profiles across storage tiers (REQ-101).
 */
public interface ConnectionProfileStore {

    /** Returns the underlying persistence mechanism type. */
    ConnectionStoreType getType();

    /** Indicates whether persistence is currently enabled for this store. */
    boolean isEnabled();

    /** Loads all persisted connection profiles into an in-memory map keyed by profile ID. */
    Map<String, ConnectionProfile> loadAll();

    /** Saves or updates an individual connection profile in the persistent store. */
    void save(ConnectionProfile profile);

    /** Saves an entire batch/map of connection profiles in the persistent store. */
    void saveAll(Map<String, ConnectionProfile> profiles);

    /** Deletes an individual connection profile from the persistent store by ID. */
    void delete(String id);

    /** Returns optimistic locking version if supported by the store tier. */
    default Optional<Long> getVersion(String id) {
        return Optional.empty();
    }

    /** Returns diagnostic information about this connection store. */
    ConnectionStoreInfo getInfo();

    /**
     * Validates that credential values use secret references (env: or file:) and
     * never persist plaintext secrets.
     */
    static void validatePersistableCredentials(ConnectionProfile profile) {
        if (profile == null || profile.getCredentials() == null || profile.getCredentials().isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : profile.getCredentials().entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!value.startsWith("env:") && !value.startsWith("file:") && !value.startsWith("secretKeyRef:")) {
                throw new IllegalArgumentException(
                        "Plaintext credential '"
                                + entry.getKey()
                                + "' cannot be persisted; use env:, file:, or secretKeyRef: references");
            }
        }
    }
}

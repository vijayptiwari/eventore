package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory connection profile store for testing and ephemeral execution.
 */
public class InMemoryConnectionProfileStore implements ConnectionProfileStore {

    private final Map<String, ConnectionProfile> store = new ConcurrentHashMap<>();
    private final boolean enabled;

    public InMemoryConnectionProfileStore(boolean enabled) {
        this.enabled = enabled;
    }

    public InMemoryConnectionProfileStore() {
        this(true);
    }

    @Override
    public ConnectionStoreType getType() {
        return ConnectionStoreType.IN_MEMORY;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Map<String, ConnectionProfile> loadAll() {
        return new LinkedHashMap<>(store);
    }

    @Override
    public void save(ConnectionProfile profile) {
        if (profile != null && profile.getId() != null) {
            ConnectionProfileStore.validatePersistableCredentials(profile);
            store.put(profile.getId(), profile);
        }
    }

    @Override
    public void saveAll(Map<String, ConnectionProfile> profiles) {
        if (profiles != null) {
            for (ConnectionProfile profile : profiles.values()) {
                save(profile);
            }
        }
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            store.remove(id);
        }
    }

    @Override
    public ConnectionStoreInfo getInfo() {
        return new ConnectionStoreInfo(
                getType(),
                isEnabled(),
                store.size(),
                false,
                "in-memory",
                Map.of("storage", "ConcurrentHashMap"));
    }
}

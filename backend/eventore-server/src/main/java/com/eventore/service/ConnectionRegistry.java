package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import com.eventore.service.store.ConnectionProfileStore;
import com.eventore.service.store.ConnectionStoreInfo;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Store of connection profiles. Uses in-memory map with pluggable persistent store
 * ({@link ConnectionProfileStore}) across File, JDBC, or Kubernetes CRD tiers (REQ-101).
 */
@Service
public class ConnectionRegistry {

    private final Map<String, ConnectionProfile> profiles = new ConcurrentHashMap<>();
    private final ConnectionProfileStore persistence;

    public ConnectionRegistry(ConnectionProfileStore persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence store must not be null");
    }

    @PostConstruct
    void loadPersistedProfiles() {
        profiles.putAll(persistence.loadAll());
    }

    public List<ConnectionProfile> list() {
        return new ArrayList<>(profiles.values());
    }

    public Optional<ConnectionProfile> find(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public ConnectionProfile save(ConnectionProfile profile) {
        Objects.requireNonNull(profile, "connection profile");
        String id = profile.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("connection profile id is required");
        }
        if (persistence.isEnabled()) {
            ConnectionProfileStore.validatePersistableCredentials(profile);
        }
        profiles.put(id, profile);
        persistence.save(profile);
        return profile;
    }

    public void delete(String id) {
        profiles.remove(id);
        persistence.delete(id);
    }

    public ConnectionProfileStore getStore() {
        return persistence;
    }

    public ConnectionStoreInfo getStoreInfo() {
        return persistence.getInfo();
    }
}

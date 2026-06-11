package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Store of connection profiles. Uses in-memory map with optional file persistence
 * ({@link ConnectionProfilePersistence}) when enabled.
 */
@Service
public class ConnectionRegistry {

    private final Map<String, ConnectionProfile> profiles = new ConcurrentHashMap<>();
    private final ConnectionProfilePersistence persistence;

    public ConnectionRegistry(ConnectionProfilePersistence persistence) {
        this.persistence = persistence;
    }

    @PostConstruct
    void loadPersistedProfiles() {
        profiles.putAll(persistence.load());
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
            ConnectionProfilePersistence.validatePersistableCredentials(profile);
        }
        profiles.put(id, profile);
        persistence.saveAll(profiles);
        return profile;
    }

    public void delete(String id) {
        profiles.remove(id);
        persistence.saveAll(profiles);
    }
}

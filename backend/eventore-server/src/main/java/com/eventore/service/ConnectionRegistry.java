package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory store of connection profiles.
 *
 * <p><strong>Security note:</strong> credentials inside {@link ConnectionProfile} are held
 * unencrypted in process memory (or as {@code env:}/{@code file:} secret references). They must
 * never be persisted to disk or any external store without encryption at rest.
 */
@Service
public class ConnectionRegistry {

    private final Map<String, ConnectionProfile> profiles = new ConcurrentHashMap<>();

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
        profiles.put(id, profile);
        return profile;
    }

    public void delete(String id) {
        profiles.remove(id);
    }
}

package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

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
        profiles.put(profile.getId(), profile);
        return profile;
    }

    public void delete(String id) {
        profiles.remove(id);
    }
}

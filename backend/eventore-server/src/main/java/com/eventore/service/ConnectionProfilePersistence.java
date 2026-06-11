package com.eventore.service;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.ConnectionProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Optional JSON file persistence for connection profiles (secret-ref credentials only). */
@Component
public class ConnectionProfilePersistence {

    private static final Logger log = LoggerFactory.getLogger(ConnectionProfilePersistence.class);
    private static final TypeReference<List<ConnectionProfile>> PROFILE_LIST =
            new TypeReference<>() {};

    private final EventoreProperties properties;
    private final ObjectMapper objectMapper;

    public ConnectionProfilePersistence(EventoreProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.getConnections().getPersistence().isEnabled();
    }

    public Map<String, ConnectionProfile> load() {
        if (!isEnabled()) {
            return new LinkedHashMap<>();
        }
        Path path = resolvePath();
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            List<ConnectionProfile> list = objectMapper.readValue(bytes, PROFILE_LIST);
            Map<String, ConnectionProfile> map = new LinkedHashMap<>();
            for (ConnectionProfile profile : list) {
                if (profile.getId() != null && !profile.getId().isBlank()) {
                    map.put(profile.getId(), profile);
                }
            }
            log.info("Loaded {} connection profile(s) from {}", map.size(), path);
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load connection profiles from " + path, e);
        }
    }

    public void saveAll(Map<String, ConnectionProfile> profiles) {
        if (!isEnabled()) {
            return;
        }
        Path path = resolvePath();
        try {
            Files.createDirectories(path.getParent());
            List<ConnectionProfile> list = new ArrayList<>(profiles.values());
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, json);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Persisted {} connection profile(s) to {}", list.size(), path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist connection profiles to " + path, e);
        }
    }

    public static void validatePersistableCredentials(ConnectionProfile profile) {
        if (profile.getCredentials() == null || profile.getCredentials().isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : profile.getCredentials().entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!value.startsWith("env:") && !value.startsWith("file:")) {
                throw new IllegalArgumentException(
                        "Plaintext credential '"
                                + entry.getKey()
                                + "' cannot be persisted; use env: or file: secret references");
            }
        }
    }

    private Path resolvePath() {
        return Path.of(properties.getConnections().getPersistence().getFilePath());
    }
}

package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
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

/**
 * File-based JSON connection profile store (REQ-101).
 */
public class FileConnectionProfileStore implements ConnectionProfileStore {

    private static final Logger log = LoggerFactory.getLogger(FileConnectionProfileStore.class);
    private static final TypeReference<List<ConnectionProfile>> PROFILE_LIST =
            new TypeReference<>() {};

    private final Path filePath;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public FileConnectionProfileStore(Path filePath, boolean enabled, ObjectMapper objectMapper) {
        this.filePath = filePath != null ? filePath : Path.of("/data/connections.json");
        this.enabled = enabled;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public FileConnectionProfileStore(String filePath, boolean enabled, ObjectMapper objectMapper) {
        this(filePath != null ? Path.of(filePath) : Path.of("/data/connections.json"), enabled, objectMapper);
    }

    @Override
    public ConnectionStoreType getType() {
        return ConnectionStoreType.FILE;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Path getFilePath() {
        return filePath;
    }

    @Override
    public Map<String, ConnectionProfile> loadAll() {
        if (!isEnabled() || !Files.exists(filePath)) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return new LinkedHashMap<>();
            }
            List<ConnectionProfile> list = objectMapper.readValue(bytes, PROFILE_LIST);
            Map<String, ConnectionProfile> map = new LinkedHashMap<>();
            for (ConnectionProfile profile : list) {
                if (profile.getId() != null && !profile.getId().isBlank()) {
                    map.put(profile.getId(), profile);
                }
            }
            log.info("Loaded {} connection profile(s) from {}", map.size(), filePath);
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load connection profiles from " + filePath, e);
        }
    }

    @Override
    public void save(ConnectionProfile profile) {
        if (profile == null || !isEnabled()) {
            return;
        }
        ConnectionProfileStore.validatePersistableCredentials(profile);
        Map<String, ConnectionProfile> current = loadAll();
        current.put(profile.getId(), profile);
        saveAll(current);
    }

    @Override
    public void saveAll(Map<String, ConnectionProfile> profiles) {
        if (!isEnabled()) {
            return;
        }
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            List<ConnectionProfile> list = profiles != null ? new ArrayList<>(profiles.values()) : new ArrayList<>();
            for (ConnectionProfile profile : list) {
                ConnectionProfileStore.validatePersistableCredentials(profile);
            }
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
            Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.write(temp, json);
            try {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Persisted {} connection profile(s) to {}", list.size(), filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist connection profiles to " + filePath, e);
        }
    }

    @Override
    public void delete(String id) {
        if (id == null) {
            return;
        }
        Map<String, ConnectionProfile> current = loadAll();
        if (current.remove(id) != null) {
            saveAll(current);
        }
    }

    @Override
    public ConnectionStoreInfo getInfo() {
        int count = 0;
        try {
            count = loadAll().size();
        } catch (Exception ignored) {
        }
        return new ConnectionStoreInfo(
                getType(),
                isEnabled(),
                count,
                false,
                filePath.toAbsolutePath().toString(),
                Map.of("filePath", filePath.toString(), "format", "JSON"));
    }
}

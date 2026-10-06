package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Relational JDBC connection profile store with distributed optimistic locking (REQ-101).
 */
public class JdbcConnectionProfileStore implements ConnectionProfileStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcConnectionProfileStore.class);

    private final DataSource dataSource;
    private final String tableName;
    private final boolean optimisticLocking;
    private final boolean enabled;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> versions = new ConcurrentHashMap<>();

    public JdbcConnectionProfileStore(
            DataSource dataSource,
            String tableName,
            boolean optimisticLocking,
            boolean enabled,
            ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.tableName = tableName != null && !tableName.isBlank() ? tableName : "eventore_connection_profiles";
        this.optimisticLocking = optimisticLocking;
        this.enabled = enabled;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        if (enabled && dataSource != null) {
            initSchema();
        }
    }

    public synchronized void initSchema() {
        if (dataSource == null) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id VARCHAR(128) PRIMARY KEY, "
                + "name VARCHAR(255) NOT NULL, "
                + "protocol VARCHAR(64) NOT NULL, "
                + "profile_json TEXT NOT NULL, "
                + "version BIGINT NOT NULL DEFAULT 1, "
                + "updated_at TIMESTAMP NOT NULL"
                + ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.info("Initialized JDBC connection store schema table: {}", tableName);
        } catch (SQLException e) {
            log.warn("Failed or skipped creating schema table {}: {}", tableName, e.getMessage());
        }
    }

    @Override
    public ConnectionStoreType getType() {
        return ConnectionStoreType.JDBC;
    }

    @Override
    public boolean isEnabled() {
        return enabled && dataSource != null;
    }

    @Override
    public Map<String, ConnectionProfile> loadAll() {
        if (!isEnabled()) {
            return new LinkedHashMap<>();
        }
        Map<String, ConnectionProfile> result = new LinkedHashMap<>();
        String sql = "SELECT id, name, protocol, profile_json, version FROM " + tableName + " ORDER BY name ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String json = rs.getString("profile_json");
                long version = rs.getLong("version");
                try {
                    ConnectionProfile profile = objectMapper.readValue(json, ConnectionProfile.class);
                    result.put(id, profile);
                    versions.put(id, version);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse connection profile JSON for id {}: {}", id, e.getMessage());
                }
            }
            log.info("Loaded {} connection profile(s) from JDBC table {}", result.size(), tableName);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load connection profiles from JDBC table " + tableName, e);
        }
    }

    @Override
    public void save(ConnectionProfile profile) {
        if (profile == null || profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalArgumentException("Connection profile id is required");
        }
        if (!isEnabled()) {
            return;
        }
        ConnectionProfileStore.validatePersistableCredentials(profile);

        String json;
        try {
            json = objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize connection profile to JSON", e);
        }

        String id = profile.getId();
        String name = profile.getName() != null ? profile.getName() : id;
        String protocol = profile.getProtocol() != null ? profile.getProtocol().name() : "GENERIC";
        Timestamp now = Timestamp.from(Instant.now());

        try (Connection conn = dataSource.getConnection()) {
            Long currentVersion = queryCurrentVersion(conn, id);

            if (currentVersion == null) {
                // INSERT
                String insertSql = "INSERT INTO " + tableName
                        + " (id, name, protocol, profile_json, version, updated_at) VALUES (?, ?, ?, ?, 1, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, id);
                    ps.setString(2, name);
                    ps.setString(3, protocol);
                    ps.setString(4, json);
                    ps.setTimestamp(5, now);
                    ps.executeUpdate();
                    versions.put(id, 1L);
                    log.debug("Inserted new profile '{}' in JDBC store (version 1)", id);
                }
            } else {
                // UPDATE with optimistic locking
                if (optimisticLocking) {
                    Long expectedVersion = versions.get(id);
                    long targetVersion = expectedVersion != null ? expectedVersion : currentVersion;
                    String updateSql = "UPDATE " + tableName
                            + " SET name = ?, protocol = ?, profile_json = ?, version = version + 1, updated_at = ?"
                            + " WHERE id = ? AND version = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, name);
                        ps.setString(2, protocol);
                        ps.setString(3, json);
                        ps.setTimestamp(4, now);
                        ps.setString(5, id);
                        ps.setLong(6, targetVersion);
                        int rows = ps.executeUpdate();
                        if (rows == 0) {
                            throw new OptimisticLockingFailureException(
                                    "Connection profile '" + id
                                            + "' was concurrently updated by another process (expected version "
                                            + targetVersion + ", current DB version " + currentVersion + ")");
                        }
                        versions.put(id, targetVersion + 1);
                        log.debug("Updated profile '{}' in JDBC store to version {}", id, targetVersion + 1);
                    }
                } else {
                    String updateSql = "UPDATE " + tableName
                            + " SET name = ?, protocol = ?, profile_json = ?, version = version + 1, updated_at = ?"
                            + " WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, name);
                        ps.setString(2, protocol);
                        ps.setString(3, json);
                        ps.setTimestamp(4, now);
                        ps.setString(5, id);
                        ps.executeUpdate();
                        versions.put(id, currentVersion + 1);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save connection profile to JDBC store: " + e.getMessage(), e);
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
        if (id == null || !isEnabled()) {
            return;
        }
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            versions.remove(id);
            log.debug("Deleted profile '{}' from JDBC store", id);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete connection profile from JDBC store: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Long> getVersion(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Long memVer = versions.get(id);
        if (memVer != null) {
            return Optional.of(memVer);
        }
        if (isEnabled()) {
            try (Connection conn = dataSource.getConnection()) {
                Long dbVer = queryCurrentVersion(conn, id);
                if (dbVer != null) {
                    versions.put(id, dbVer);
                    return Optional.of(dbVer);
                }
            } catch (SQLException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public ConnectionStoreInfo getInfo() {
        int count = 0;
        String dbUrl = "unknown";
        if (isEnabled()) {
            try (Connection conn = dataSource.getConnection()) {
                dbUrl = conn.getMetaData().getURL();
                try (Statement statement = conn.createStatement();
                     ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                    if (result.next()) count = result.getInt(1);
                }
            } catch (Exception ignored) {
            }
        }
        return new ConnectionStoreInfo(
                getType(),
                isEnabled(),
                count,
                optimisticLocking,
                "table: " + tableName,
                Map.of(
                        "tableName", tableName,
                        "optimisticLocking", String.valueOf(optimisticLocking),
                        "databaseUrl", dbUrl));
    }

    private Long queryCurrentVersion(Connection conn, String id) throws SQLException {
        String sql = "SELECT version FROM " + tableName + " WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("version");
                }
            }
        }
        return null;
    }
}

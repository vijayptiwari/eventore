package com.eventore.service;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionProfilePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledPersistenceSkipsLoadAndSave() {
        EventoreProperties properties = new EventoreProperties();
        ConnectionProfilePersistence persistence =
                new ConnectionProfilePersistence(properties, new ObjectMapper());

        assertTrue(persistence.load().isEmpty());
        persistence.saveAll(Map.of("x", profile("x")));
        assertTrue(persistence.load().isEmpty());
    }

    @Test
    void enabledPersistenceRoundTripsProfiles() throws Exception {
        Path file = tempDir.resolve("connections.json");
        EventoreProperties properties = propertiesFor(file);
        ConnectionProfilePersistence persistence =
                new ConnectionProfilePersistence(properties, new ObjectMapper());

        ConnectionProfile profile = profile("conn-kafka");
        profile.setName("Kafka Dev");
        Map<String, ConnectionProfile> map = new LinkedHashMap<>();
        map.put(profile.getId(), profile);
        persistence.saveAll(map);

        Map<String, ConnectionProfile> loaded = persistence.load();
        assertEquals(1, loaded.size());
        assertEquals("Kafka Dev", loaded.get("conn-kafka").getName());
        assertTrue(Files.exists(file));
    }

    @Test
    void saveRejectsPlaintextCredentialsWhenEnabled() {
        Path file = tempDir.resolve("connections.json");
        EventoreProperties properties = propertiesFor(file);
        ConnectionProfilePersistence persistence =
                new ConnectionProfilePersistence(properties, new ObjectMapper());

        ConnectionProfile profile = profile("conn-secret");
        profile.setCredentials(Map.of("password", "plaintext-secret"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ConnectionProfilePersistence.validatePersistableCredentials(profile));
        assertTrue(ex.getMessage().contains("Plaintext credential"));
    }

    @Test
    void saveAcceptsEnvAndFileCredentialRefs() {
        ConnectionProfile profile = profile("conn-ref");
        profile.setCredentials(Map.of("password", "env:EVENTORE_BROKER_PASSWORD", "cert", "file:/secrets/tls.pem"));

        ConnectionProfilePersistence.validatePersistableCredentials(profile);
    }

    private static EventoreProperties propertiesFor(Path file) {
        EventoreProperties properties = new EventoreProperties();
        properties.getConnections().getPersistence().setEnabled(true);
        properties.getConnections().getPersistence().setFilePath(file.toString());
        return properties;
    }

    private static ConnectionProfile profile(String id) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId(id);
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("localhost:9092");
        return profile;
    }
}

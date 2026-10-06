package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.eventore.domain.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

class JdbcConnectionProfileStoreTest {

    private DataSource dataSource;
    private JdbcConnectionProfileStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:eventore_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.objectMapper = new ObjectMapper();
        this.store = new JdbcConnectionProfileStore(
                dataSource,
                "eventore_connection_profiles",
                true,
                true,
                objectMapper);
    }

    @Test
    void storeTypeIsJdbcAndEnabled() {
        assertEquals(ConnectionStoreType.JDBC, store.getType());
        assertTrue(store.isEnabled());
        ConnectionStoreInfo info = store.getInfo();
        assertTrue(info.supportsOptimisticLocking());
        assertTrue(info.location().contains("eventore_connection_profiles"));
    }

    @Test
    void saveAndLoadAllRoundTripsProfile() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka-1");
        profile.setName("Kafka Staging");
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("kafka:9092");
        profile.setCredentials(Map.of("password", "env:KAFKA_SECRET"));

        store.save(profile);

        Map<String, ConnectionProfile> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("conn-kafka-1"));
        assertEquals("Kafka Staging", loaded.get("conn-kafka-1").getName());
        assertEquals("kafka:9092", loaded.get("conn-kafka-1").getBrokerUrl());
        assertEquals("env:KAFKA_SECRET", loaded.get("conn-kafka-1").getCredentials().get("password"));
        assertEquals(1L, store.getVersion("conn-kafka-1").orElse(0L));
    }

    @Test
    void updateIncrementsVersionWithOptimisticLocking() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-pulsar");
        profile.setName("Pulsar Cluster");
        profile.setProtocol(ProtocolType.PULSAR);

        store.save(profile);
        assertEquals(1L, store.getVersion("conn-pulsar").orElse(0L));

        profile.setName("Pulsar Cluster Renamed");
        store.save(profile);
        assertEquals(2L, store.getVersion("conn-pulsar").orElse(0L));

        ConnectionProfile updated = store.loadAll().get("conn-pulsar");
        assertEquals("Pulsar Cluster Renamed", updated.getName());
    }

    @Test
    void optimisticLockingDetectsConcurrentUpdateConflict() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-mqtt-concurrent");
        profile.setName("MQTT Broker");
        profile.setProtocol(ProtocolType.MQTT);

        store.save(profile);
        assertEquals(1L, store.getVersion("conn-mqtt-concurrent").orElse(0L));

        // Simulate another store instance updating the database directly
        JdbcConnectionProfileStore rivalStore = new JdbcConnectionProfileStore(
                dataSource,
                "eventore_connection_profiles",
                true,
                true,
                objectMapper);
        ConnectionProfile rivalProfile = rivalStore.loadAll().get("conn-mqtt-concurrent");
        rivalProfile.setName("MQTT Updated by Rival Pod");
        rivalStore.save(rivalProfile);

        // Reading diagnostics must not refresh the version of a stale in-memory profile.
        assertEquals(1, store.getInfo().activeProfileCount());
        // Now the original store instance tries to update with its stale version
        profile.setName("MQTT Overwrite Attempt");
        assertThrows(OptimisticLockingFailureException.class, () -> store.save(profile));
    }

    @Test
    void plaintextCredentialsAreRejected() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-insecure");
        profile.setCredentials(Map.of("password", "supersecret-plaintext"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(profile));
        assertTrue(ex.getMessage().contains("Plaintext credential"));
    }

    @Test
    void deleteRemovesProfileFromJdbcTable() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-to-delete");
        profile.setName("Ephemeral");
        profile.setProtocol(ProtocolType.RABBITMQ);

        store.save(profile);
        assertEquals(1, store.loadAll().size());

        store.delete("conn-to-delete");
        assertEquals(0, store.loadAll().size());
        assertTrue(store.getVersion("conn-to-delete").isEmpty());
    }
}

package com.eventore.service.store;

import com.eventore.domain.CloudProvider;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.eventore.domain.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class K8sCrdConnectionProfileStoreTest {

    @TempDir
    Path tempDir;

    private K8sCrdConnectionProfileStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
        this.store = new K8sCrdConnectionProfileStore(tempDir, true, objectMapper);
    }

    @Test
    void storeTypeIsK8sCrd() {
        assertEquals(ConnectionStoreType.K8S_CRD, store.getType());
        assertTrue(store.isEnabled());
        ConnectionStoreInfo info = store.getInfo();
        assertEquals(ConnectionStoreType.K8S_CRD, info.type());
        assertEquals("eventore.com/v1alpha1", info.details().get("crdGroup"));
    }

    @Test
    void saveWritesValidK8sCrdManifestAndLoadsBack() throws Exception {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka-prod");
        profile.setName("Kafka Production");
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setCloudProvider(CloudProvider.AWS);
        profile.setBrokerUrl("b-1.msk.aws.com:9092");
        profile.setCredentials(Map.of("password", "env:MSK_PASSWORD"));
        profile.setProperties(Map.of("security.protocol", "SASL_SSL"));

        store.save(profile);

        Path crdFile = tempDir.resolve("conn-kafka-prod.json");
        assertTrue(Files.exists(crdFile));
        String content = Files.readString(crdFile);
        assertTrue(content.contains("\"apiVersion\" : \"eventore.com/v1alpha1\""));
        assertTrue(content.contains("\"kind\" : \"EventoreConnection\""));
        assertTrue(content.contains("\"eventore.com/protocol\" : \"KAFKA\""));

        Map<String, ConnectionProfile> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        ConnectionProfile roundtrip = loaded.get("conn-kafka-prod");
        assertNotNull(roundtrip);
        assertEquals("Kafka Production", roundtrip.getName());
        assertEquals(ProtocolType.KAFKA, roundtrip.getProtocol());
        assertEquals(CloudProvider.AWS, roundtrip.getCloudProvider());
        assertEquals("b-1.msk.aws.com:9092", roundtrip.getBrokerUrl());
        assertEquals("env:MSK_PASSWORD", roundtrip.getCredentials().get("password"));
        assertEquals("SASL_SSL", roundtrip.getProperties().get("security.protocol"));
    }

    @Test
    void plaintextCredentialsRejectedInK8sStore() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-bad");
        profile.setCredentials(Map.of("secret", "unencrypted-password"));

        assertThrows(IllegalArgumentException.class, () -> store.save(profile));
    }

    @Test
    void normalizedIdsCannotOverwriteOrDeleteAnotherProfile() {
        ConnectionProfile original = new ConnectionProfile();
        original.setId("Orders");
        store.save(original);
        ConnectionProfile collision = new ConnectionProfile();
        collision.setId("orders");
        assertThrows(IllegalArgumentException.class, () -> store.save(collision));
        assertThrows(IllegalArgumentException.class, () -> store.delete("orders"));
        assertTrue(store.loadAll().containsKey("Orders"));
    }

    @Test
    void deleteRemovesCrdManifest() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-temp");
        profile.setProtocol(ProtocolType.MQTT);

        store.save(profile);
        assertEquals(1, store.loadAll().size());

        store.delete("conn-temp");
        assertEquals(0, store.loadAll().size());
        assertFalse(Files.exists(tempDir.resolve("conn-temp.json")));
    }
}

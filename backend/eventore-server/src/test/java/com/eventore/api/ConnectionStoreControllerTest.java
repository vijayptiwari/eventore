package com.eventore.api;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.eventore.domain.ProtocolType;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.store.ConnectionStoreInfo;
import com.eventore.service.store.DelegatingConnectionProfileStore;
import com.eventore.service.store.InMemoryConnectionProfileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionStoreControllerTest {

    private ConnectionRegistry registry;
    private DelegatingConnectionProfileStore delegator;
    private ConnectionStoreController controller;

    @BeforeEach
    void setUp() {
        InMemoryConnectionProfileStore fileStore = new InMemoryConnectionProfileStore(true) {
            @Override
            public ConnectionStoreType getType() {
                return ConnectionStoreType.FILE;
            }
        };
        InMemoryConnectionProfileStore jdbcStore = new InMemoryConnectionProfileStore(true) {
            @Override
            public ConnectionStoreType getType() {
                return ConnectionStoreType.JDBC;
            }
        };

        this.delegator = new DelegatingConnectionProfileStore(fileStore);
        this.delegator.registerStore(jdbcStore);
        this.registry = new ConnectionRegistry(delegator);
        this.controller = new ConnectionStoreController(registry, org.mockito.Mockito.mock(com.eventore.security.DeploymentModePolicy.class));
    }

    @Test
    void migrationIsForbiddenInReadonlyMode() {
        var properties = new com.eventore.config.EventoreProperties();
        properties.setDeploymentMode(com.eventore.security.DeploymentMode.READONLY);
        var policy = new com.eventore.security.DeploymentModePolicy(properties, org.mockito.Mockito.mock(com.eventore.controlplane.ControlPlaneRegistry.class));
        var readonlyController = new ConnectionStoreController(registry, policy);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> readonlyController.migrate(new ConnectionStoreController.MigrateStoreRequest(ConnectionStoreType.JDBC)));
        assertEquals(ConnectionStoreType.FILE, delegator.getType());
    }

    @Test
    void getStoreInfoReturnsActiveStoreMetadata() {
        ResponseEntity<ConnectionStoreInfo> response = controller.getStoreInfo();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ConnectionStoreType.FILE, response.getBody().type());
        assertTrue(response.getBody().enabled());
    }

    @Test
    void migrateSuccessfullyTransfersProfilesToTargetStore() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka");
        profile.setName("Kafka Main");
        profile.setProtocol(ProtocolType.KAFKA);
        registry.save(profile);

        ConnectionStoreController.MigrateStoreRequest request =
                new ConnectionStoreController.MigrateStoreRequest(ConnectionStoreType.JDBC);

        ResponseEntity<ConnectionStoreController.MigrateStoreResponse> response = controller.migrate(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().status());
        assertEquals(1, response.getBody().migratedProfiles());
        assertEquals(ConnectionStoreType.JDBC, response.getBody().activeStore());

        // Verify active store is now JDBC
        assertEquals(ConnectionStoreType.JDBC, registry.getStoreInfo().type());
    }

    @Test
    void migrateRejectsNullTargetType() {
        ConnectionStoreController.MigrateStoreRequest request =
                new ConnectionStoreController.MigrateStoreRequest(null);

        ResponseEntity<ConnectionStoreController.MigrateStoreResponse> response = controller.migrate(request);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("ERROR", response.getBody().status());
    }
}

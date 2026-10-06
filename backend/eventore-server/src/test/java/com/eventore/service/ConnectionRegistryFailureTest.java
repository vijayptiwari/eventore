package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import com.eventore.service.store.ConnectionProfileStore;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ConnectionRegistryFailureTest {
    @Test
    void disabledPersistenceAcceptsEphemeralCredentials() {
        var properties = new com.eventore.config.EventoreProperties();
        ConnectionRegistry registry = new ConnectionRegistry(new ConnectionProfilePersistence(properties, new com.fasterxml.jackson.databind.ObjectMapper()));
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("ephemeral");
        profile.setCredentials(java.util.Map.of("password", "runtime-only"));
        registry.save(profile);
        assertThat(registry.find("ephemeral")).contains(profile);
    }

    @Test
    void failedWritesLeaveLiveStateUnchanged() {
        ConnectionProfileStore store = mock(ConnectionProfileStore.class);
        ConnectionRegistry registry = new ConnectionRegistry(store);
        ConnectionProfile original = new ConnectionProfile();
        original.setId("connection");
        registry.save(original);
        ConnectionProfile update = new ConnectionProfile();
        update.setId("connection");
        doThrow(new IllegalStateException("disk full")).when(store).save(update);
        assertThatThrownBy(() -> registry.save(update)).hasMessage("disk full");
        assertThat(registry.find("connection")).contains(original);
        doThrow(new IllegalStateException("disk full")).when(store).delete("connection");
        assertThatThrownBy(() -> registry.delete("connection")).hasMessage("disk full");
        assertThat(registry.find("connection")).contains(original);
    }
}

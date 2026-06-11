package com.eventore.service;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
    }

    @Test
    void saveStoresProfileById() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-mqtt");
        profile.setName("MQTT");
        profile.setProtocol(ProtocolType.MQTT);
        profile.setBrokerUrl("localhost:1883");

        ConnectionProfile saved = registry.save(profile);

        assertSame(profile, saved);
        assertEquals(profile, registry.find(profile.getId()).orElseThrow());
    }

    @Test
    void saveRejectsNullProfile() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> registry.save(null));
        assertEquals("connection profile", ex.getMessage());
    }

    @Test
    void saveRejectsBlankId() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("   ");
        profile.setProtocol(ProtocolType.MQTT);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.save(profile));
        assertEquals("connection profile id is required", ex.getMessage());
    }
}

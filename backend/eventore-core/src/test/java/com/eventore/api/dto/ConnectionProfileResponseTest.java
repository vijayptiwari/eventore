package com.eventore.api.dto;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionProfileResponseTest {

    @Test
    void fromMapsProfileWithoutCredentials() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setName("prod-kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("localhost:9092");
        profile.setProperties(Map.of("region", "us-east-1"));

        ConnectionProfileResponse response = ConnectionProfileResponse.from(profile);

        assertEquals(profile.getId(), response.getId());
        assertEquals("prod-kafka", response.getName());
        assertEquals(ProtocolType.KAFKA, response.getProtocol());
        assertEquals("localhost:9092", response.getBrokerUrl());
        assertEquals(Map.of("region", "us-east-1"), response.getProperties());
        assertFalse(response.isHasCredentials());
    }

    @Test
    void fromFlagsCredentialsPresent() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setCredentials(Map.of("password", "secret"));

        ConnectionProfileResponse response = ConnectionProfileResponse.from(profile);

        assertTrue(response.isHasCredentials());
    }

    @Test
    void fromRejectsNullProfile() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ConnectionProfileResponse.from(null));
        assertEquals("connection profile is required", ex.getMessage());
    }
}

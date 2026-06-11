package com.eventore.inspect.pulsar;

import com.eventore.domain.ProtocolType;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PulsarMessagingInspectorTest {

    private PulsarMessagingInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new PulsarMessagingInspector();
    }

    @Test
    void protocolIsPulsar() {
        assertEquals(ProtocolType.PULSAR, inspector.protocol());
    }

    @Test
    void capabilitiesIncludePulsarOperations() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("subscriptions"));
        assertTrue(features.contains("backlog"));
        assertTrue(features.contains("message-search"));
        assertFalse(features.contains("topic-create"));
    }

    @Test
    void describeConsumerGroupReturnsSubscriptionMetadata() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650", null, null);

        var detail = inspector.describeConsumerGroup(profile, "my-sub");

        assertEquals("my-sub", detail.getGroupId());
        assertEquals("subscription", detail.getState());
    }

    @Test
    void searchMessagesRequiresTopic() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650", null, null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> inspector.searchMessages(profile, new MessageSearchRequest()));
        assertEquals("topic is required", ex.getMessage());
    }

    @Test
    void adminUrlPrefersExplicitAdminUrlProperty() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.PULSAR,
                "pulsar://broker:6650",
                Map.of("adminUrl", "https://admin.example:8443"),
                null);

        assertEquals("https://admin.example:8443", inspector.adminUrl(profile));
    }

    @Test
    void adminUrlDerivesHttpEndpointFromBrokerUrl() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://broker:6650", null, null);

        assertEquals("http://broker:8080", inspector.adminUrl(profile));
    }

    @Test
    void adminUrlFallsBackToLocalhostForUnrecognizedBrokerUrl() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "broker-without-scheme", null, null);

        assertEquals("http://localhost:8080", inspector.adminUrl(profile));
    }
}

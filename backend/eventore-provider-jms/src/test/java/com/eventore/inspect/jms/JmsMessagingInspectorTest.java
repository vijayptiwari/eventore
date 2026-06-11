package com.eventore.inspect.jms;

import com.eventore.domain.ProtocolType;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmsMessagingInspectorTest {

    private JmsMessagingInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new JmsMessagingInspector();
    }

    @Test
    void protocolIsJms() {
        assertEquals(ProtocolType.JMS, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeQueuesAndTopics() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("queues"));
        assertTrue(features.contains("topics"));
    }

    @Test
    void clusterInfoReflectsBroker() {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "tcp://artemis:61616", null, null);

        var info = inspector.clusterInfo(profile);

        assertEquals("tcp://artemis:61616", info.getClusterId());
        assertEquals("Apache Artemis", info.getAttributes().get("implementation"));
    }

    @Test
    void listTopicsFiltersByName() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.JMS, "tcp://artemis:61616", Map.of("queue", "orders", "topic", "broadcast"), null);

        assertEquals(1, inspector.listTopics(profile, "order").size());
        assertEquals("orders", inspector.listTopics(profile, "order").get(0).getName());
        assertEquals(2, inspector.listTopics(profile, null).size());
    }

    @Test
    void listTopicsWithNonMatchingFilterReturnsEmpty() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.JMS, "tcp://artemis:61616", Map.of("queue", "orders", "topic", "broadcast"), null);

        assertEquals(0, inspector.listTopics(profile, "no-such-destination").size());
    }

    @Test
    void describeTopicReturnsMatchingDestinationOrFallback() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.JMS, "tcp://artemis:61616", Map.of("queue", "orders"), null);

        assertEquals("orders", inspector.describeTopic(profile, "orders").getName());
        assertEquals("unknown", inspector.describeTopic(profile, "unknown").getName());
    }

    @Test
    void consumerGroupsAreNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "tcp://artemis:61616", null, null);

        assertEquals(0, inspector.listConsumerGroups(profile).size());
        assertThrows(UnsupportedOperationException.class, () -> inspector.describeConsumerGroup(profile, "g1"));
        assertThrows(UnsupportedOperationException.class, () -> inspector.searchMessages(profile, new MessageSearchRequest()));
    }
}

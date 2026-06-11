package com.eventore.inspect.azure;

import com.eventore.connector.azure.AzureServiceBusMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureServiceBusMessagingInspectorTest {

    @Mock
    private AzureServiceBusMessagingConnector connector;

    @InjectMocks
    private AzureServiceBusMessagingInspector inspector;

    private void stubConnectorDestinations() {
        when(connector.listDestinations(any()))
                .thenReturn(List.of(
                        new TopicRef("orders", "queue", ProtocolType.AZURE_SERVICE_BUS),
                        new TopicRef("broadcast", "topic", ProtocolType.AZURE_SERVICE_BUS)));
    }

    @Test
    void protocolIsAzureServiceBus() {
        assertEquals(ProtocolType.AZURE_SERVICE_BUS, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeServiceBusOperations() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("queues"));
        assertTrue(features.contains("topics"));
        assertTrue(features.contains("queue-detail"));
        assertFalse(features.contains("message-search"));
    }

    @Test
    void clusterInfoReflectsAzureMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS, "sb://namespace.servicebus.windows.net", null, null);

        var info = inspector.clusterInfo(profile);

        assertEquals("sb://namespace.servicebus.windows.net", info.getClusterId());
        assertEquals("AZURE", info.getAttributes().get("cloudProvider"));
        assertEquals("Service Bus", info.getAttributes().get("service"));
    }

    @Test
    void listTopicsFiltersByNameAndPreservesType() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        var topics = inspector.listTopics(profile, "order");

        assertEquals(1, topics.size());
        assertEquals("orders", topics.get(0).getName());
        assertEquals("queue", topics.get(0).getConfig().get("type"));
    }

    @Test
    void describeTopicReturnsMatchingDestinationOrFallback() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        assertEquals("broadcast", inspector.describeTopic(profile, "broadcast").getName());
        assertEquals("missing", inspector.describeTopic(profile, "missing").getName());
    }

    @Test
    void consumerGroupsAreNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        assertEquals(0, inspector.listConsumerGroups(profile).size());
        assertThrows(UnsupportedOperationException.class, () -> inspector.describeConsumerGroup(profile, "sub"));
        assertThrows(UnsupportedOperationException.class, () -> inspector.searchMessages(profile, new MessageSearchRequest()));
    }
}

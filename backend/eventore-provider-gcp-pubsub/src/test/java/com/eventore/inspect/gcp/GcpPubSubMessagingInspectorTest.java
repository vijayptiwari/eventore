package com.eventore.inspect.gcp;

import com.eventore.connector.gcp.GcpPubSubMessagingConnector;
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
class GcpPubSubMessagingInspectorTest {

    @Mock
    private GcpPubSubMessagingConnector connector;

    @InjectMocks
    private GcpPubSubMessagingInspector inspector;

    private void stubConnectorDestinations() {
        when(connector.listDestinations(any()))
                .thenReturn(List.of(
                        new TopicRef("orders", "topic", ProtocolType.GCP_PUBSUB),
                        new TopicRef("events", "topic", ProtocolType.GCP_PUBSUB)));
    }

    @Test
    void protocolIsGcpPubSub() {
        assertEquals(ProtocolType.GCP_PUBSUB, inspector.protocol());
    }

    @Test
    void capabilitiesIncludePubSubOperations() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("topics"));
        assertFalse(features.contains("message-search"));
    }

    @Test
    void clusterInfoReflectsGcpMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB, "fallback", Map.of("projectId", "eventore-dev"), null);

        var info = inspector.clusterInfo(profile);

        assertEquals("eventore-dev", info.getClusterId());
        assertEquals("GCP", info.getAttributes().get("cloudProvider"));
        assertEquals("Pub/Sub", info.getAttributes().get("service"));
    }

    @Test
    void listTopicsFiltersByName() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);

        assertEquals(1, inspector.listTopics(profile, "order").size());
        assertEquals("orders", inspector.listTopics(profile, "order").get(0).getName());
    }

    @Test
    void describeTopicIncludesProjectId() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB, "fallback", Map.of("projectId", "eventore-dev"), null);

        var topic = inspector.describeTopic(profile, "orders");

        assertEquals("orders", topic.getName());
        assertEquals("eventore-dev", topic.getConfig().get("projectId"));
    }

    @Test
    void describeConsumerGroupReturnsSubscriptionState() {
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);

        var detail = inspector.describeConsumerGroup(profile, "orders-sub");

        assertEquals("orders-sub", detail.getGroupId());
        assertEquals("subscription", detail.getState());
    }

    @Test
    void searchMessagesIsNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);

        assertThrows(UnsupportedOperationException.class, () -> inspector.searchMessages(profile, new MessageSearchRequest()));
    }
}

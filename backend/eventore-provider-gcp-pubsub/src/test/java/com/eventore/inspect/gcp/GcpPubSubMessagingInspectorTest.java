package com.eventore.inspect.gcp;

import com.eventore.connector.gcp.GcpPubSubMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private GcpPubSubMessagingInspector inspector() {
        return new GcpPubSubMessagingInspector(
                connector,
                profile -> List.of(subscriptionSummary("orders-sub", "orders")),
                req -> {
                    ConsumerGroupDetail detail = new ConsumerGroupDetail();
                    detail.setGroupId(req.groupId());
                    detail.setState("ACTIVE");
                    return detail;
                },
                req -> List.of(backlogOffset("orders", 3)));
    }

    private static ConsumerGroupSummary subscriptionSummary(String sub, String topic) {
        ConsumerGroupSummary summary = new ConsumerGroupSummary();
        summary.setGroupId(sub);
        summary.setState("ACTIVE");
        summary.putAttribute("topic", topic);
        return summary;
    }

    private static GroupOffset backlogOffset(String topic, long lag) {
        GroupOffset offset = new GroupOffset();
        offset.setTopic(topic);
        offset.setLag(lag);
        return offset;
    }

    private void stubConnectorDestinations() {
        when(connector.listDestinations(any()))
                .thenReturn(List.of(
                        new TopicRef("orders", "topic", ProtocolType.GCP_PUBSUB),
                        new TopicRef("events", "topic", ProtocolType.GCP_PUBSUB)));
    }

    @Test
    void protocolIsGcpPubSub() {
        assertEquals(ProtocolType.GCP_PUBSUB, inspector().protocol());
    }

    @Test
    void capabilitiesIncludeSubscriptionsAndBacklog() {
        var features = inspector().capabilities().getFeatures();
        assertTrue(features.contains("topics"));
        assertTrue(features.contains("subscriptions"));
        assertTrue(features.contains("backlog"));
        assertFalse(features.contains("message-search"));
    }

    @Test
    void clusterInfoReflectsGcpMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB, "fallback", Map.of("projectId", "eventore-dev"), null);

        var info = inspector().clusterInfo(profile);

        assertEquals("eventore-dev", info.getClusterId());
        assertEquals("GCP", info.getAttributes().get("cloudProvider"));
        assertEquals("Pub/Sub", info.getAttributes().get("service"));
    }

    @Test
    void listConsumerGroupsReturnsSubscriptionSummaries() {
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);
        var groups = inspector().listConsumerGroups(profile);
        assertEquals(1, groups.size());
        assertEquals("orders-sub", groups.get(0).getGroupId());
        assertEquals("orders", groups.get(0).getAttributes().get("topic"));
    }

    @Test
    void consumerLagReturnsBacklogRows() {
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);
        var lag = inspector().consumerLag(profile, "orders-sub", null);
        assertEquals(1, lag.size());
        assertEquals(3, lag.get(0).getLag());
        assertEquals("orders", lag.get(0).getTopic());
    }

    @Test
    void listTopicsFiltersByName() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);

        assertEquals(1, new GcpPubSubMessagingInspector(connector).listTopics(profile, "order").size());
    }

    @Test
    void searchMessagesIsNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "eventore-dev", null, null);

        assertThrows(
                UnsupportedOperationException.class,
                () -> inspector().searchMessages(profile, new MessageSearchRequest()));
    }
}

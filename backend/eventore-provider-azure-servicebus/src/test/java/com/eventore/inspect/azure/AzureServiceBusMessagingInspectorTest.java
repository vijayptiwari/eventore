package com.eventore.inspect.azure;

import com.eventore.connector.azure.AzureServiceBusMessagingConnector;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureServiceBusMessagingInspectorTest {

    @Mock
    private AzureServiceBusMessagingConnector connector;

    private AzureServiceBusMessagingInspector inspector() {
        return new AzureServiceBusMessagingInspector(
                connector,
                profile -> List.of(subscriptionSummary()),
                req -> {
                    ConsumerGroupDetail detail = new ConsumerGroupDetail();
                    detail.setGroupId(req.groupId());
                    detail.setState("ACTIVE");
                    return detail;
                },
                req -> List.of(queueBacklog("orders", 7)),
                req -> List.of(peekMessage("orders", "peek-body")));
    }

    private static ConsumerGroupSummary subscriptionSummary() {
        ConsumerGroupSummary summary = new ConsumerGroupSummary();
        summary.setGroupId("sub-1");
        summary.setState("ACTIVE");
        summary.putAttribute("topic", "broadcast");
        summary.putAttribute("activeMessageCount", "2");
        summary.putAttribute("deadLetterMessageCount", "0");
        return summary;
    }

    private static GroupOffset queueBacklog(String entity, long lag) {
        GroupOffset offset = new GroupOffset();
        offset.setTopic(entity);
        offset.setLag(lag);
        return offset;
    }

    private static UnifiedMessage peekMessage(String entity, String payload) {
        UnifiedMessage msg = new UnifiedMessage();
        msg.setDestination(entity);
        msg.setProtocol(ProtocolType.AZURE_SERVICE_BUS);
        msg.setDirection(MessageDirection.INBOUND);
        msg.setPayload(payload);
        msg.putHeader("peek", "true");
        return msg;
    }

    private void stubConnectorDestinations() {
        when(connector.listDestinations(any()))
                .thenReturn(List.of(
                        new TopicRef("orders", "queue", ProtocolType.AZURE_SERVICE_BUS),
                        new TopicRef("broadcast", "topic", ProtocolType.AZURE_SERVICE_BUS)));
    }

    @Test
    void protocolIsAzureServiceBus() {
        assertEquals(ProtocolType.AZURE_SERVICE_BUS, inspector().protocol());
    }

    @Test
    void capabilitiesIncludeSubscriptionsPeekAndBacklog() {
        var features = inspector().capabilities().getFeatures();
        assertTrue(features.contains("queues"));
        assertTrue(features.contains("topics"));
        assertTrue(features.contains("subscriptions"));
        assertTrue(features.contains("message-search"));
        assertTrue(features.contains("backlog"));
    }

    @Test
    void clusterInfoReflectsAzureMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS, "sb://namespace.servicebus.windows.net", null, null);

        var info = inspector().clusterInfo(profile);

        assertEquals("sb://namespace.servicebus.windows.net", info.getClusterId());
        assertEquals("AZURE", info.getAttributes().get("cloudProvider"));
        assertEquals("Service Bus", info.getAttributes().get("service"));
    }

    @Test
    void listConsumerGroupsReturnsSubscriptionSummaries() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        var groups = inspector().listConsumerGroups(profile);
        assertEquals(1, groups.size());
        assertEquals("sub-1", groups.get(0).getGroupId());
        assertEquals("2", groups.get(0).getAttributes().get("activeMessageCount"));
    }

    @Test
    void describeConsumerGroupReturnsMetadata() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        var detail = inspector().describeConsumerGroup(profile, "sub-1");
        assertEquals("sub-1", detail.getGroupId());
        assertEquals("ACTIVE", detail.getState());
    }

    @Test
    void consumerLagExposesQueueBacklog() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        var lag = inspector().consumerLag(profile, "orders", null);
        assertEquals(7, lag.get(0).getLag());
    }

    @Test
    void searchMessagesPeeksWithoutRemoving() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("orders");
        var messages = inspector().searchMessages(profile, request);
        assertEquals(1, messages.size());
        assertEquals("peek-body", messages.get(0).getPayload());
        assertEquals("true", messages.get(0).getHeaders().get("peek"));
    }

    @Test
    void listTopicsFiltersByNameAndPreservesType() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        var topics = inspector().listTopics(profile, "order");

        assertEquals(1, topics.size());
        assertEquals("orders", topics.get(0).getName());
        assertEquals("queue", topics.get(0).getConfig().get("type"));
    }
}

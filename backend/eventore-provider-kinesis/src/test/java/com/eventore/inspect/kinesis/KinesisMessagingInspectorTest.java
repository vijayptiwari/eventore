package com.eventore.inspect.kinesis;

import com.eventore.connector.kinesis.KinesisMessagingConnector;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KinesisMessagingInspectorTest {

    @Mock
    private KinesisMessagingConnector connector;

    @InjectMocks
    private KinesisMessagingInspector inspector;

    private void stubConnectorDestinations() {
        when(connector.listDestinations(any()))
                .thenReturn(List.of(
                        new TopicRef("orders-stream", "stream", ProtocolType.KINESIS),
                        new TopicRef("events-stream", "stream", ProtocolType.KINESIS)));
    }

    @Test
    void protocolIsKinesis() {
        assertEquals(ProtocolType.KINESIS, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeStreamOperations() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("streams"));
        assertTrue(features.contains("stream-detail"));
        assertFalse(features.contains("message-search"));
    }

    @Test
    void clusterInfoReflectsAwsMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS, "us-west-2", Map.of("region", "us-west-2"), null);

        var info = inspector.clusterInfo(profile);

        assertEquals("us-west-2", info.getClusterId());
        assertEquals("AWS", info.getAttributes().get("cloudProvider"));
        assertEquals("Kinesis", info.getAttributes().get("service"));
    }

    @Test
    void listTopicsFiltersByName() {
        stubConnectorDestinations();
        var profile = StreamTestFixtures.profile(ProtocolType.KINESIS, "us-east-1", null, null);

        assertEquals(1, inspector.listTopics(profile, "order").size());
        assertEquals("orders-stream", inspector.listTopics(profile, "order").get(0).getName());
        assertEquals(2, inspector.listTopics(profile, null).size());
    }

    @Test
    void consumerGroupsAreNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.KINESIS, "us-east-1", null, null);

        assertEquals(0, inspector.listConsumerGroups(profile).size());
        assertThrows(UnsupportedOperationException.class, () -> inspector.describeConsumerGroup(profile, "g1"));
        assertThrows(UnsupportedOperationException.class, () -> inspector.searchMessages(profile, new MessageSearchRequest()));
    }
}

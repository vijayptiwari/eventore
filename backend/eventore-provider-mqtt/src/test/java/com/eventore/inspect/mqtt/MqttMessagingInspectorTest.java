package com.eventore.inspect.mqtt;

import com.eventore.domain.ProtocolType;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttMessagingInspectorTest {

    private MqttMessagingInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new MqttMessagingInspector();
    }

    @Test
    void protocolIsMqtt() {
        assertEquals(ProtocolType.MQTT, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeBrokerAndTopicFilter() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("broker-info"));
        assertTrue(features.contains("topic-filter"));
    }

    @Test
    void clusterInfoReflectsBrokerUrlAndTopicFilter() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.MQTT, "tcp://broker:1883", Map.of("topicFilter", "home/#"), null);

        var info = inspector.clusterInfo(profile);

        assertEquals("tcp://broker:1883", info.getClusterId());
        assertEquals("tcp://broker:1883", info.getAttributes().get("brokerUrl"));
        assertEquals("home/#", info.getAttributes().get("topicFilter"));
    }

    @Test
    void listTopicsUsesProfileFilterOrNameFilter() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.MQTT, "tcp://broker:1883", Map.of("topicFilter", "fleet/#"), null);

        var topics = inspector.listTopics(profile, null);

        assertEquals(1, topics.size());
        assertEquals("fleet/#", topics.get(0).getName());
        assertEquals("topic-filter", topics.get(0).getConfig().get("type"));
    }

    @Test
    void listTopicsWithoutProfileFilterFallsBackToNameFilterThenHash() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "tcp://broker:1883", null, null);

        assertEquals("custom/#", inspector.listTopics(profile, "custom/#").get(0).getName());
        assertEquals("#", inspector.listTopics(profile, null).get(0).getName());
    }

    @Test
    void describeTopicSetsQosHint() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "tcp://broker:1883", null, null);

        var topic = inspector.describeTopic(profile, "devices/1/status");

        assertEquals("devices/1/status", topic.getName());
        assertEquals("0,1,2", topic.getConfig().get("qos-hint"));
    }

    @Test
    void brokerInfoReturnsManagementMetadata() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.MQTT, "ssl://broker:8883", Map.of("topicFilter", "#"), null);

        var info = inspector.brokerInfo(profile);

        assertEquals("ssl://broker:8883", info.get("brokerUrl"));
        assertEquals("#", info.get("topicFilter"));
    }

    @Test
    void consumerGroupsAreNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "tcp://broker:1883", null, null);

        assertEquals(0, inspector.listConsumerGroups(profile).size());
        assertThrows(UnsupportedOperationException.class, () -> inspector.describeConsumerGroup(profile, "g1"));
        assertEquals(0, inspector.consumerLag(profile, "g1", null).size());
    }

    @Test
    void searchMessagesIsNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "tcp://broker:1883", null, null);

        assertThrows(
                UnsupportedOperationException.class,
                () -> inspector.searchMessages(profile, new MessageSearchRequest()));
    }
}

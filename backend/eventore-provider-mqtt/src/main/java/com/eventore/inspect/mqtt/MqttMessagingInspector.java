package com.eventore.inspect.mqtt;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.domain.InspectModels.ClusterInfo;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.domain.InspectModels.ProtocolInspectCapabilities;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import com.eventore.inspect.spi.MessagingInspector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MqttMessagingInspector implements MessagingInspector {

    @Override
    public ProtocolType protocol() {
        return ProtocolType.MQTT;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("broker-info", "topics", "topic-filter"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId(profile.getBrokerUrl());
        info.getAttributes().put("brokerUrl", profile.getBrokerUrl());
        info.getAttributes().put("topicFilter", profile.propertyOrDefault("topicFilter", "#"));
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        throw new UnsupportedOperationException("MQTT has no consumer groups");
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        TopicDetail td = new TopicDetail();
        td.setName(profile.propertyOrDefault("topicFilter", nameFilter != null ? nameFilter : "#"));
        td.getConfig().put("type", "topic-filter");
        td.getConfig().put("note", "MQTT brokers do not expose topic lists; use filters and live stream");
        return List.of(td);
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        TopicDetail td = new TopicDetail();
        td.setName(topic);
        td.getConfig().put("qos-hint", "0,1,2");
        return td;
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        return List.of();
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        throw new UnsupportedOperationException("Use live stream with topic subscription");
    }

    @Override
    public Map<String, Object> brokerInfo(ConnectionProfile profile) {
        Map<String, Object> m = new HashMap<>();
        m.put("brokerUrl", profile.getBrokerUrl());
        m.put("topicFilter", profile.propertyOrDefault("topicFilter", "#"));
        return m;
    }
}

package com.eventore.inspect.jms;

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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JmsMessagingInspector implements MessagingInspector {

    @Override
    public ProtocolType protocol() {
        return ProtocolType.JMS;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("broker-info", "queues", "topics"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId(profile.getBrokerUrl());
        info.getAttributes().put("broker", profile.getBrokerUrl());
        info.getAttributes().put("implementation", "Apache Artemis");
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        throw new UnsupportedOperationException("JMS has no Kafka-style consumer groups");
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        List<TopicDetail> list = new ArrayList<>();
        String queue = profile.propertyOrDefault("queue", "eventore.queue");
        if (nameFilter == null || nameFilter.isBlank() || queue.contains(nameFilter)) {
            TopicDetail q = new TopicDetail();
            q.setName(queue);
            q.getConfig().put("destinationType", "queue");
            list.add(q);
        }
        String topic = profile.property("topic");
        if (topic != null
                && (nameFilter == null || nameFilter.isBlank() || topic.contains(nameFilter))) {
            TopicDetail t = new TopicDetail();
            t.setName(topic);
            t.getConfig().put("destinationType", "topic");
            list.add(t);
        }
        return list;
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        return listTopics(profile, topic).stream()
                .filter(t -> t.getName().equals(topic))
                .findFirst()
                .orElseGet(() -> {
                    TopicDetail td = new TopicDetail();
                    td.setName(topic);
                    return td;
                });
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        return List.of();
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        throw new UnsupportedOperationException("Use live JMS stream consumer");
    }
}

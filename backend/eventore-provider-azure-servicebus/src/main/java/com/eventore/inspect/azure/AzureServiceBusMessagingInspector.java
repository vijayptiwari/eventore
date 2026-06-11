package com.eventore.inspect.azure;

import com.eventore.connector.azure.AzureServiceBusMessagingConnector;
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
public class AzureServiceBusMessagingInspector implements MessagingInspector {

    private final AzureServiceBusMessagingConnector connector;

    public AzureServiceBusMessagingInspector(AzureServiceBusMessagingConnector connector) {
        this.connector = connector;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.AZURE_SERVICE_BUS;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("cluster", "queues", "topics", "queue-detail"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId(profile.getBrokerUrl());
        info.putAttribute("cloudProvider", "AZURE");
        info.putAttribute("service", "Service Bus");
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        throw new UnsupportedOperationException("Service Bus uses subscriptions on topics");
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        List<TopicDetail> list = new ArrayList<>();
        for (var ref : connector.listDestinations(profile)) {
            if (nameFilter != null
                    && !nameFilter.isBlank()
                    && !ref.getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                continue;
            }
            TopicDetail td = new TopicDetail();
            td.setName(ref.getName());
            td.putConfig("type", ref.getType());
            list.add(td);
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
        throw new UnsupportedOperationException("Use peek via Azure portal or live view");
    }
}

package com.eventore.inspect.azure;

import com.eventore.connector.azure.AzureServiceBusMessagingConnector;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
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
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AzureServiceBusMessagingInspector implements MessagingInspector {

    private final AzureServiceBusMessagingConnector connector;
    private final Function<ConnectionProfile, List<ConsumerGroupSummary>> subscriptionLister;
    private final Function<DescribeRequest, ConsumerGroupDetail> subscriptionDescriber;
    private final Function<BacklogRequest, List<GroupOffset>> backlogReader;
    private final Function<PeekRequest, List<UnifiedMessage>> messagePeeker;

    @Autowired
    public AzureServiceBusMessagingInspector(AzureServiceBusMessagingConnector connector) {
        this(
                connector,
                profile -> AzureServiceBusInspectSupport.listSubscriptions(profile, connector.listDestinations(profile)),
                req -> AzureServiceBusInspectSupport.describeSubscription(
                        req.profile(), req.groupId(), connector.listDestinations(req.profile())),
                req -> AzureServiceBusInspectSupport.entityBacklog(
                        req.profile(), req.entityId(), req.topicFilter(), connector.listDestinations(req.profile())),
                req -> AzureServiceBusInspectSupport.peekMessages(
                        req.profile(), req.request(), connector.listDestinations(req.profile())));
    }

    AzureServiceBusMessagingInspector(
            AzureServiceBusMessagingConnector connector,
            Function<ConnectionProfile, List<ConsumerGroupSummary>> subscriptionLister,
            Function<DescribeRequest, ConsumerGroupDetail> subscriptionDescriber,
            Function<BacklogRequest, List<GroupOffset>> backlogReader,
            Function<PeekRequest, List<UnifiedMessage>> messagePeeker) {
        this.connector = connector;
        this.subscriptionLister = subscriptionLister;
        this.subscriptionDescriber = subscriptionDescriber;
        this.backlogReader = backlogReader;
        this.messagePeeker = messagePeeker;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.AZURE_SERVICE_BUS;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of(
                "cluster", "queues", "topics", "queue-detail", "subscriptions", "message-search", "backlog"));
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
        return subscriptionLister.apply(profile);
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        return subscriptionDescriber.apply(new DescribeRequest(profile, groupId));
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        List<TopicDetail> list = new ArrayList<>();
        for (TopicRef ref : connector.listDestinations(profile)) {
            if (nameFilter != null
                    && !nameFilter.isBlank()
                    && !ref.getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                continue;
            }
            TopicDetail td = new TopicDetail();
            td.setName(ref.getName());
            td.putConfig("type", ref.getType());
            if ("queue".equalsIgnoreCase(ref.getType())) {
                try {
                    AzureServiceBusInspectSupport.enrichQueueDetail(
                            profile,
                            new AzureServiceBusInspectSupport.TopicDetailHolder(ref.getName(), ref.getType(), td));
                } catch (RuntimeException e) {
                    td.putConfig("note", "Queue runtime unavailable: " + e.getMessage());
                }
            }
            list.add(td);
        }
        return list;
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        TopicDetail td = listTopics(profile, topic).stream()
                .filter(t -> t.getName().equals(topic))
                .findFirst()
                .orElseGet(() -> {
                    TopicDetail fallback = new TopicDetail();
                    fallback.setName(topic);
                    return fallback;
                });
        return td;
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        return backlogReader.apply(new BacklogRequest(profile, groupId, topicFilter));
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        return messagePeeker.apply(new PeekRequest(profile, request));
    }

    record DescribeRequest(ConnectionProfile profile, String groupId) {}

    record BacklogRequest(ConnectionProfile profile, String entityId, String topicFilter) {}

    record PeekRequest(ConnectionProfile profile, MessageSearchRequest request) {}
}

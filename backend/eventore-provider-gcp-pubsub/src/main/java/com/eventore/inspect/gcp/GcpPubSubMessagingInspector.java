package com.eventore.inspect.gcp;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.gcp.GcpPubSubMessagingConnector;
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
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class GcpPubSubMessagingInspector implements MessagingInspector {

    private final GcpPubSubMessagingConnector connector;
    private final Function<ConnectionProfile, List<ConsumerGroupSummary>> subscriptionLister;
    private final Function<DescribeRequest, ConsumerGroupDetail> subscriptionDescriber;
    private final Function<BacklogRequest, List<GroupOffset>> backlogReader;

    public GcpPubSubMessagingInspector(GcpPubSubMessagingConnector connector) {
        this(
                connector,
                GcpPubSubInspectSupport::listSubscriptions,
                req -> GcpPubSubInspectSupport.describeSubscription(req.profile(), req.groupId()),
                req -> GcpPubSubInspectSupport.subscriptionBacklog(
                        req.profile(), req.groupId(), req.topicFilter()));
    }

    GcpPubSubMessagingInspector(
            GcpPubSubMessagingConnector connector,
            Function<ConnectionProfile, List<ConsumerGroupSummary>> subscriptionLister,
            Function<DescribeRequest, ConsumerGroupDetail> subscriptionDescriber,
            Function<BacklogRequest, List<GroupOffset>> backlogReader) {
        this.connector = connector;
        this.subscriptionLister = subscriptionLister;
        this.subscriptionDescriber = subscriptionDescriber;
        this.backlogReader = backlogReader;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.GCP_PUBSUB;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("cluster", "topics", "subscriptions", "backlog"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId(CloudClientSupport.gcpProjectId(profile));
        info.putAttribute("cloudProvider", "GCP");
        info.putAttribute("service", "Pub/Sub");
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
        List<TopicDetail> topics = new ArrayList<>();
        for (var ref : connector.listDestinations(profile)) {
            if (nameFilter != null
                    && !nameFilter.isBlank()
                    && !ref.getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                continue;
            }
            TopicDetail td = new TopicDetail();
            td.setName(ref.getName());
            topics.add(td);
        }
        return topics;
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        TopicDetail td = new TopicDetail();
        td.setName(topic);
        td.putConfig("projectId", CloudClientSupport.gcpProjectId(profile));
        return td;
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        return backlogReader.apply(new BacklogRequest(profile, groupId, topicFilter));
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        throw new UnsupportedOperationException("Use live view or GCP console for message sampling");
    }

    record DescribeRequest(ConnectionProfile profile, String groupId) {}

    record BacklogRequest(ConnectionProfile profile, String groupId, String topicFilter) {}
}

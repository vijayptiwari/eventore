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
import org.springframework.stereotype.Component;

@Component
public class GcpPubSubMessagingInspector implements MessagingInspector {

    private final GcpPubSubMessagingConnector connector;

    public GcpPubSubMessagingInspector(GcpPubSubMessagingConnector connector) {
        this.connector = connector;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.GCP_PUBSUB;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("cluster", "topics"));
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
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        ConsumerGroupDetail d = new ConsumerGroupDetail();
        d.setGroupId(groupId);
        d.setState("subscription");
        return d;
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
        return List.of();
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        throw new UnsupportedOperationException("Use live view or GCP console for message sampling");
    }
}

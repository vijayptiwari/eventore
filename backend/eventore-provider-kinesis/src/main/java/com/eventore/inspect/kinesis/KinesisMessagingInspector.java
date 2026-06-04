package com.eventore.inspect.kinesis;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.kinesis.KinesisMessagingConnector;
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
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamSummaryRequest;
import software.amazon.awssdk.services.kinesis.model.StreamDescriptionSummary;

@Component
public class KinesisMessagingInspector implements MessagingInspector {

    private final KinesisMessagingConnector connector;

    public KinesisMessagingInspector(KinesisMessagingConnector connector) {
        this.connector = connector;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.KINESIS;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of("cluster", "streams", "stream-detail", "shard-info", "message-search"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId(CloudClientSupport.awsRegion(profile).id());
        info.getAttributes().put("cloudProvider", "AWS");
        info.getAttributes().put("service", "Kinesis");
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        throw new UnsupportedOperationException("Kinesis uses consumers per application, not Kafka groups");
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
        try (KinesisClient client = KinesisClient.builder()
                .region(CloudClientSupport.awsRegion(profile))
                .credentialsProvider(CloudClientSupport.awsCredentials(profile))
                .build()) {
            StreamDescriptionSummary summary = client.describeStreamSummary(
                            DescribeStreamSummaryRequest.builder().streamName(topic).build())
                    .streamDescriptionSummary();
            td.setPartitionCount(summary.openShardCount());
            td.getConfig().put("status", summary.streamStatusAsString());
            td.getConfig().put("retentionHours", String.valueOf(summary.retentionPeriodHours()));
        } catch (Exception e) {
            td.getConfig().put("error", e.getMessage());
        }
        return td;
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        return List.of();
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        throw new UnsupportedOperationException("Use live view on Kinesis stream; shard sampling via AWS CLI");
    }
}

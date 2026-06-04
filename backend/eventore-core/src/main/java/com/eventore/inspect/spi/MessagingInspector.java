package com.eventore.inspect.spi;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.domain.InspectModels.ClusterInfo;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.domain.InspectModels.ProtocolInspectCapabilities;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import java.util.List;
import java.util.Map;

public interface MessagingInspector {

    ProtocolType protocol();

    ProtocolInspectCapabilities capabilities();

    ClusterInfo clusterInfo(ConnectionProfile profile);

    List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile);

    ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId);

    List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter);

    TopicDetail describeTopic(ConnectionProfile profile, String topic);

    List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter);

    List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request);

    /** Generic key-value broker metadata for non-Kafka protocols. */
    default Map<String, Object> brokerInfo(ConnectionProfile profile) {
        return Map.of();
    }
}

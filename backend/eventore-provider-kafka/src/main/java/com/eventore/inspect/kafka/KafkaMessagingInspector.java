package com.eventore.inspect.kafka;

import com.eventore.connector.kafka.KafkaClientSupport;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.dataplane.ResourceNotFoundException;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.domain.InspectModels.BrokerNode;
import com.eventore.inspect.domain.InspectModels.ClusterInfo;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupMember;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.domain.InspectModels.ProtocolInspectCapabilities;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import com.eventore.inspect.domain.InspectModels.TopicPartitionInfo;
import com.eventore.inspect.spi.MessagingInspector;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessagingInspector implements MessagingInspector {

    @Override
    public ProtocolType protocol() {
        return ProtocolType.KAFKA;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of(
                "cluster", "brokers", "topics", "topic-detail", "consumer-groups",
                "group-detail", "lag", "message-search",
                "publish-headers", "topic-create", "topic-delete", "topic-flush",
                "topic-configs", "acl-list", "acl-create", "acl-delete", "acl-replace"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            DescribeClusterResult cluster = admin.describeCluster();
            ClusterInfo info = new ClusterInfo();
            info.setClusterId(cluster.clusterId().get());
            for (Node node : cluster.nodes().get()) {
                BrokerNode b = new BrokerNode();
                b.setId(node.id());
                b.setHost(node.host());
                b.setPort(node.port());
                b.setRack(node.rack());
                info.addBroker(b);
            }
            info.putAttribute("controller", String.valueOf(cluster.controller().get().id()));
            return info;
        } catch (Exception e) {
            throw new IllegalStateException("Kafka cluster info failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            ListConsumerGroupsResult result = admin.listConsumerGroups();
            Collection<ConsumerGroupListing> listings = result.all().get();
            List<ConsumerGroupSummary> summaries = new ArrayList<>();
            for (ConsumerGroupListing listing : listings) {
                ConsumerGroupSummary s = new ConsumerGroupSummary();
                s.setGroupId(listing.groupId());
                s.setState(listing.state().map(Enum::name).orElse("UNKNOWN"));
                s.setProtocolType(null);
                summaries.add(s);
            }
            Map<String, ConsumerGroupDescription> described =
                    admin.describeConsumerGroups(listings.stream().map(ConsumerGroupListing::groupId).toList())
                            .all()
                            .get();
            for (ConsumerGroupSummary s : summaries) {
                ConsumerGroupDescription d = described.get(s.getGroupId());
                if (d != null) {
                    s.setMemberCount(d.members().size());
                    s.setState(d.state().name());
                }
            }
            summaries.sort((a, b) -> a.getGroupId().compareToIgnoreCase(b.getGroupId()));
            return summaries;
        } catch (Exception e) {
            throw new IllegalStateException("List consumer groups failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            ConsumerGroupDescription desc =
                    admin.describeConsumerGroups(List.of(groupId)).all().get().get(groupId);
            if (desc == null) {
                throw new ResourceNotFoundException("Consumer group not found: " + groupId);
            }
            ConsumerGroupDetail detail = new ConsumerGroupDetail();
            detail.setGroupId(groupId);
            detail.setState(desc.state().name());
            detail.setPartitionAssignor(desc.partitionAssignor());
            for (MemberDescription member : desc.members()) {
                GroupMember m = new GroupMember();
                m.setMemberId(member.consumerId());
                m.setClientId(member.clientId());
                m.setHost(member.host());
                member.assignment().topicPartitions().forEach(tp ->
                        m.addAssignment(tp.topic() + "-" + tp.partition()));
                detail.addMember(m);
            }
            detail.setOffsets(consumerLag(profile, groupId, null));
            return detail;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Describe consumer group failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            Set<String> names = admin.listTopics().names().get();
            List<String> filtered = names.stream()
                    .filter(n -> nameFilter == null
                            || nameFilter.isBlank()
                            || n.toLowerCase().contains(nameFilter.toLowerCase()))
                    .sorted()
                    .toList();
            if (filtered.isEmpty()) {
                return List.of();
            }
            Map<String, TopicDescription> described =
                    admin.describeTopics(filtered).allTopicNames().get();
            List<TopicDetail> topics = new ArrayList<>();
            for (String name : filtered) {
                TopicDescription td = described.get(name);
                if (td != null) {
                    topics.add(toTopicDetail(td));
                }
            }
            return topics;
        } catch (Exception e) {
            throw new IllegalStateException("List topics failed: " + e.getMessage(), e);
        }
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            TopicDescription td = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            if (td == null) {
                throw new ResourceNotFoundException("Topic not found: " + topic);
            }
            return toTopicDetail(td);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Describe topic failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            ListConsumerGroupOffsetsResult offsetsResult = admin.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                    offsetsResult.partitionsToOffsetAndMetadata().get();
            if (committed.isEmpty()) {
                return List.of();
            }
            Map<TopicPartition, OffsetSpec> endSpecs = new HashMap<>();
            for (TopicPartition tp : committed.keySet()) {
                endSpecs.put(tp, OffsetSpec.latest());
            }
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(endSpecs).all().get();
            List<GroupOffset> lags = new ArrayList<>();
            for (Map.Entry<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> entry :
                    committed.entrySet()) {
                TopicPartition tp = entry.getKey();
                if (topicFilter != null
                        && !topicFilter.isBlank()
                        && !tp.topic().contains(topicFilter)) {
                    continue;
                }
                long offset = entry.getValue().offset();
                long logEnd = endOffsets.get(tp).offset();
                GroupOffset go = new GroupOffset();
                go.setTopic(tp.topic());
                go.setPartition(tp.partition());
                go.setOffset(offset);
                go.setLogEndOffset(logEnd);
                go.setLag(Math.max(0, logEnd - offset));
                lags.add(go);
            }
            lags.sort((a, b) -> {
                int t = a.getTopic().compareTo(b.getTopic());
                return t != 0 ? t : Integer.compare(a.getPartition(), b.getPartition());
            });
            return lags;
        } catch (Exception e) {
            throw new IllegalStateException("Consumer lag failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        int max = request.getMaxMessages() != null ? Math.min(request.getMaxMessages(), 200) : 50;
        String group = "eventore-search-" + UUID.randomUUID();
        Properties props = KafkaClientSupport.consumerProps(profile, group);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> partitions = resolvePartitions(profile, request.getTopic(), request.getPartition());
            consumer.assign(partitions);
            if ("earliest".equalsIgnoreCase(request.getStartAt())) {
                consumer.seekToBeginning(partitions);
            } else {
                consumer.seekToEnd(partitions);
                for (TopicPartition tp : partitions) {
                    long end = consumer.position(tp);
                    consumer.seek(tp, Math.max(0, end - max * 10L));
                }
            }
            if (request.getFromOffset() != null && request.getPartition() != null) {
                TopicPartition tp = new TopicPartition(request.getTopic(), Integer.parseInt(request.getPartition()));
                consumer.seek(tp, request.getFromOffset());
            }
            List<UnifiedMessage> found = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (found.size() < max && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (!matches(record, request)) {
                        continue;
                    }
                    found.add(toMessage(profile, record));
                    if (found.size() >= max) {
                        break;
                    }
                }
            }
            return found;
        } catch (Exception e) {
            throw new IllegalStateException("Message search failed: " + e.getMessage(), e);
        }
    }

    private List<TopicPartition> resolvePartitions(ConnectionProfile profile, String topic, String partition)
            throws Exception {
        if (partition != null && !partition.isBlank()) {
            return List.of(new TopicPartition(topic, Integer.parseInt(partition)));
        }
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            TopicDescription td = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            return td.partitions().stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
        }
    }

    private boolean matches(ConsumerRecord<String, byte[]> record, MessageSearchRequest request) {
        if (request.getKeyContains() != null
                && !request.getKeyContains().isBlank()
                && (record.key() == null || !record.key().contains(request.getKeyContains()))) {
            return false;
        }
        if (request.getPayloadContains() != null && !request.getPayloadContains().isBlank()) {
            String payload = record.value() != null
                    ? new String(record.value(), StandardCharsets.UTF_8)
                    : "";
            if (!payload.contains(request.getPayloadContains())) {
                return false;
            }
        }
        if (request.getToOffset() != null && record.offset() > request.getToOffset()) {
            return false;
        }
        return true;
    }

    private UnifiedMessage toMessage(ConnectionProfile profile, ConsumerRecord<String, byte[]> record) {
        UnifiedMessage msg = new UnifiedMessage();
        msg.setConnectionId(profile.getId());
        msg.setProtocol(ProtocolType.KAFKA);
        msg.setDestination(record.topic());
        msg.setDirection(MessageDirection.INBOUND);
        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(record.value());
        msg.setPayload(decoded.text());
        msg.setContentType(decoded.contentType());
        msg.putHeader("partition", String.valueOf(record.partition()));
        msg.putHeader("offset", String.valueOf(record.offset()));
        if (record.key() != null) {
            msg.putHeader("key", record.key());
        }
        return msg;
    }

    private TopicDetail toTopicDetail(TopicDescription td) {
        TopicDetail detail = new TopicDetail();
        detail.setName(td.name());
        detail.setPartitionCount(td.partitions().size());
        detail.setReplicationFactor(
                td.partitions().isEmpty() ? 0 : td.partitions().get(0).replicas().size());
        for (org.apache.kafka.common.TopicPartitionInfo pi : td.partitions()) {
            TopicPartitionInfo info = new TopicPartitionInfo();
            info.setPartition(pi.partition());
            info.setLeader(pi.leader() != null ? pi.leader().id() : -1);
            pi.replicas().forEach(n -> info.addReplica(n.id()));
            pi.isr().forEach(n -> info.addIsr(n.id()));
            detail.addPartition(info);
        }
        return detail;
    }
}

package com.eventore.inspect.pulsar;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PulsarMessagingInspector implements MessagingInspector {

    private static final Logger log = LoggerFactory.getLogger(PulsarMessagingInspector.class);

    @Override
    public ProtocolType protocol() {
        return ProtocolType.PULSAR;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of(
                "cluster", "topics", "subscriptions", "backlog", "lag", "message-search"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        try (PulsarAdmin admin = admin(profile)) {
            info.setClusterId(profile.getBrokerUrl());
            info.putAttribute("clusters", String.join(",", admin.clusters().getClusters()));
            info.putAttribute("tenants", String.join(",", admin.tenants().getTenants()));
        } catch (Exception e) {
            info.putAttribute("error", e.getMessage());
        }
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        List<ConsumerGroupSummary> groups = new ArrayList<>();
        try (PulsarAdmin admin = admin(profile)) {
            for (String ns : admin.namespaces().getNamespaces(tenant(profile))) {
                for (String topic : admin.topics().getList(ns)) {
                    for (String sub : admin.topics().getSubscriptions(topic)) {
                        ConsumerGroupSummary s = new ConsumerGroupSummary();
                        s.setGroupId(sub);
                        s.setState("Active");
                        groups.add(s);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Pulsar subscription listing failed for connection {}; returning partial results",
                    profile.getId(), e);
        }
        return groups;
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
        try (PulsarAdmin admin = admin(profile)) {
            for (String ns : admin.namespaces().getNamespaces(tenant(profile))) {
                for (String topic : admin.topics().getList(ns)) {
                    String shortName = topic.contains("/") ? topic.substring(topic.lastIndexOf('/') + 1) : topic;
                    if (nameFilter != null
                            && !nameFilter.isBlank()
                            && !shortName.toLowerCase().contains(nameFilter.toLowerCase())) {
                        continue;
                    }
                    TopicDetail td = new TopicDetail();
                    td.setName(shortName);
                    td.putConfig("fullName", topic);
                    topics.add(td);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar list topics: " + e.getMessage(), e);
        }
        return topics;
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        TopicDetail td = new TopicDetail();
        td.setName(topic);
        try (PulsarAdmin admin = admin(profile)) {
            String full = normalizeTopic(profile, topic);
            td.putConfig("partitions", String.valueOf(admin.topics().getPartitionedTopicMetadata(full).partitions));
        } catch (Exception e) {
            td.putConfig("error", e.getMessage());
        }
        return td;
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        List<GroupOffset> lags = new ArrayList<>();
        try (PulsarAdmin admin = admin(profile)) {
            for (String ns : admin.namespaces().getNamespaces(tenant(profile))) {
                for (String topic : admin.topics().getList(ns)) {
                    if (topicFilter != null && !topic.contains(topicFilter)) {
                        continue;
                    }
                    try {
                        var stats = admin.topics().getStats(topic);
                        if (stats.getSubscriptions() != null && stats.getSubscriptions().containsKey(groupId)) {
                            GroupOffset go = new GroupOffset();
                            go.setTopic(topic);
                            go.setLag(stats.getSubscriptions().get(groupId).getMsgBacklog());
                            lags.add(go);
                        }
                    } catch (Exception e) {
                        // subscription may not exist on topic
                        log.debug("Skipping Pulsar topic {} while computing backlog for subscription {}",
                                topic, groupId, e);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar backlog: " + e.getMessage(), e);
        }
        return lags;
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        int max = request.getMaxMessages() != null ? Math.min(request.getMaxMessages(), 100) : 50;
        String full = normalizeTopic(profile, request.getTopic());
        List<UnifiedMessage> found = new ArrayList<>();
        try (PulsarClient client = PulsarClient.builder().serviceUrl(profile.getBrokerUrl()).build()) {
            try (Reader<byte[]> reader = client.newReader(Schema.BYTES)
                    .topic(full)
                    .startMessageId(MessageId.earliest)
                    .create()) {
                long deadline = System.currentTimeMillis() + 10_000;
                while (found.size() < max && System.currentTimeMillis() < deadline) {
                    org.apache.pulsar.client.api.Message<byte[]> msg = reader.readNext(2, TimeUnit.SECONDS);
                    if (msg == null) {
                        break;
                    }
                    String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                    if (request.getPayloadContains() != null
                            && !request.getPayloadContains().isBlank()
                            && !payload.contains(request.getPayloadContains())) {
                        continue;
                    }
                    UnifiedMessage um = new UnifiedMessage();
                    um.setConnectionId(profile.getId());
                    um.setProtocol(ProtocolType.PULSAR);
                    um.setDestination(msg.getTopicName());
                    um.setPayload(payload);
                    um.putHeader("messageId", msg.getMessageId().toString());
                    found.add(um);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar message search failed: " + e.getMessage(), e);
        }
        return found;
    }

    private String tenant(ConnectionProfile profile) {
        return profile.propertyOrDefault("tenant", "public");
    }

    private String normalizeTopic(ConnectionProfile profile, String destination) {
        if (destination.startsWith("persistent://") || destination.startsWith("non-persistent://")) {
            return destination;
        }
        return "persistent://" + tenant(profile) + "/default/" + destination;
    }

    private PulsarAdmin admin(ConnectionProfile profile) throws Exception {
        return PulsarAdmin.builder().serviceHttpUrl(adminUrl(profile)).build();
    }

    /**
     * Resolves the admin HTTP endpoint. An explicitly configured {@code adminUrl}
     * property always wins; otherwise the URL is derived from the broker URL.
     * Visible for tests.
     */
    String adminUrl(ConnectionProfile profile) {
        String adminUrl = profile.property("adminUrl");
        if (adminUrl != null && !adminUrl.isBlank()) {
            return adminUrl;
        }
        String http = profile.getBrokerUrl().replace("pulsar://", "http://").replace(":6650", ":8080");
        if (!http.startsWith("http")) {
            http = "http://localhost:8080";
        }
        return http;
    }
}

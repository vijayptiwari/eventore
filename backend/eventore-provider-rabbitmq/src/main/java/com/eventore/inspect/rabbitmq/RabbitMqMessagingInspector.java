package com.eventore.inspect.rabbitmq;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqMessagingInspector implements MessagingInspector {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessagingInspector.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public RabbitMqMessagingInspector() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Visible for tests so the management HTTP layer can be stubbed deterministically. */
    RabbitMqMessagingInspector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.RABBITMQ;
    }

    @Override
    public ProtocolInspectCapabilities capabilities() {
        ProtocolInspectCapabilities c = new ProtocolInspectCapabilities();
        c.setFeatures(List.of(
                "broker-info", "queues", "queue-detail", "message-search", "lag", "message-get"));
        return c;
    }

    @Override
    public ClusterInfo clusterInfo(ConnectionProfile profile) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterId("rabbitmq");
        try {
            JsonNode overview = getJson(profile, "/api/overview");
            if (overview.has("cluster_name")) {
                info.setClusterId(overview.get("cluster_name").asText());
            }
            info.putAttribute("management", managementBase(profile));
        } catch (Exception e) {
            info.putAttribute("note", "Enable management plugin or set managementPort property");
            info.putAttribute("error", e.getMessage());
        }
        return info;
    }

    @Override
    public List<ConsumerGroupSummary> listConsumerGroups(ConnectionProfile profile) {
        return List.of();
    }

    @Override
    public ConsumerGroupDetail describeConsumerGroup(ConnectionProfile profile, String groupId) {
        throw new UnsupportedOperationException("RabbitMQ uses queues, not consumer groups");
    }

    @Override
    public List<TopicDetail> listTopics(ConnectionProfile profile, String nameFilter) {
        List<TopicDetail> queues = new ArrayList<>();
        try {
            JsonNode arr = getJson(profile, "/api/queues");
            if (arr.isArray()) {
                for (JsonNode q : arr) {
                    String name = q.get("name").asText();
                    if (nameFilter != null
                            && !nameFilter.isBlank()
                            && !name.toLowerCase().contains(nameFilter.toLowerCase())) {
                        continue;
                    }
                    TopicDetail td = new TopicDetail();
                    td.setName(name);
                    // NOTE: RabbitMQ queues have no partitions, so partitionCount is
                    // deliberately repurposed to carry the queue's consumer count.
                    // Existing API consumers rely on this; do not change without a
                    // coordinated contract update.
                    td.setPartitionCount(q.path("consumers").asInt(0));
                    td.putConfig("messages", String.valueOf(q.path("messages").asInt()));
                    td.putConfig("state", q.path("state").asText(""));
                    queues.add(td);
                }
            }
        } catch (Exception e) {
            log.debug("RabbitMQ management API queue listing failed for connection {}; "
                    + "returning configured default queue", profile.getId(), e);
            TopicDetail fallback = new TopicDetail();
            fallback.setName(profile.propertyOrDefault("queue", "eventore.queue"));
            fallback.putConfig("note", "Management API unavailable: " + e.getMessage());
            queues.add(fallback);
        }
        return queues;
    }

    @Override
    public TopicDetail describeTopic(ConnectionProfile profile, String topic) {
        try {
            String vhost = encVhost(profile);
            JsonNode q = getJson(profile, "/api/queues/" + vhost + "/" + enc(topic));
            TopicDetail td = new TopicDetail();
            td.setName(topic);
            td.putConfig("messages", String.valueOf(q.path("messages").asInt()));
            td.putConfig("consumers", String.valueOf(q.path("consumers").asInt()));
            td.putConfig("message_bytes", String.valueOf(q.path("message_bytes").asLong()));
            return td;
        } catch (Exception e) {
            throw new IllegalStateException("Queue detail failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GroupOffset> consumerLag(ConnectionProfile profile, String groupId, String topicFilter) {
        List<GroupOffset> rows = new ArrayList<>();
        for (TopicDetail q : listTopics(profile, topicFilter)) {
            GroupOffset go = new GroupOffset();
            go.setTopic(q.getName());
            go.setLag(Long.parseLong(q.getConfig().getOrDefault("messages", "0")));
            rows.add(go);
        }
        return rows;
    }

    @Override
    public List<UnifiedMessage> searchMessages(ConnectionProfile profile, MessageSearchRequest request) {
        String queue = request.getTopic() != null ? request.getTopic() : profile.propertyOrDefault("queue", "eventore.queue");
        int count = request.getMaxMessages() != null ? Math.min(request.getMaxMessages(), 100) : 50;
        try {
            String vhost = encVhost(profile);
            String body = "{\"count\":" + count + ",\"ackmode\":\"reject_requeue_true\",\"encoding\":\"auto\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(managementBase(profile) + "/api/queues/" + vhost + "/" + enc(queue) + "/get"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", basicAuth(profile))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            JsonNode arr = objectMapper.readTree(res.body());
            List<UnifiedMessage> messages = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    JsonNode payload = item.path("payload");
                    String text = payload.isTextual() ? payload.asText() : payload.toString();
                    if (request.getPayloadContains() != null
                            && !request.getPayloadContains().isBlank()
                            && !text.contains(request.getPayloadContains())) {
                        continue;
                    }
                    UnifiedMessage msg = new UnifiedMessage();
                    msg.setConnectionId(profile.getId());
                    msg.setProtocol(ProtocolType.RABBITMQ);
                    msg.setDestination(queue);
                    msg.putHeader("routingKey", item.path("routing_key").asText(""));
                    msg.setPayload(text);
                    messages.add(msg);
                }
            }
            return messages;
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ message get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> brokerInfo(ConnectionProfile profile) {
        Map<String, Object> info = new HashMap<>();
        info.put("managementUrl", managementBase(profile));
        try {
            info.put("overview", objectMapper.convertValue(getJson(profile, "/api/overview"), Map.class));
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return info;
    }

    private JsonNode getJson(ConnectionProfile profile, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(managementBase(profile) + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", basicAuth(profile))
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
        }
        return objectMapper.readTree(res.body());
    }

    private String managementBase(ConnectionProfile profile) {
        String port = profile.propertyOrDefault("managementPort", "15672");
        com.eventore.connector.rabbitmq.RabbitMqBrokerUrls.Endpoint endpoint =
                com.eventore.connector.rabbitmq.RabbitMqBrokerUrls.parse(profile.getBrokerUrl());
        String scheme = "true".equalsIgnoreCase(profile.propertyOrDefault("managementTls", "false"))
                ? "https"
                : "http";
        return scheme + "://" + endpoint.host() + ":" + port;
    }

    private String basicAuth(ConnectionProfile profile) {
        String user = profile.credential("username");
        String pass = profile.credential("password");
        if (user == null || pass == null) {
            log.warn(
                    "Connection '{}' has no explicit RabbitMQ management credentials; "
                            + "falling back to default guest/guest",
                    profile.getId());
        }
        if (user == null) {
            user = "guest";
        }
        if (pass == null) {
            pass = "guest";
        }
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private String encVhost(ConnectionProfile profile) {
        return enc(profile.propertyOrDefault("vhost", "/"));
    }

    private String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

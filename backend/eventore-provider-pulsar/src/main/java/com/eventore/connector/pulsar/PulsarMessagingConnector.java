package com.eventore.connector.pulsar;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.stereotype.Component;

@Component
public class PulsarMessagingConnector implements MessagingConnector {

    private final Map<String, PulsarClient> clients = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.PULSAR;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (PulsarClient client = buildClient(profile)) {
            client.getPartitionsForTopic("persistent://public/default/test").get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // topic may not exist; admin list is enough
            try (PulsarAdmin admin = buildAdmin(profile)) {
                admin.namespaces().getNamespaces("public");
            } catch (Exception ex) {
                throw new IllegalStateException("Pulsar connection failed: " + ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        try (PulsarAdmin admin = buildAdmin(profile)) {
            for (String ns : admin.namespaces().getNamespaces("public")) {
                for (String topic : admin.topics().getList(ns)) {
                    String shortName = topic.contains("/") ? topic.substring(topic.lastIndexOf('/') + 1) : topic;
                    refs.add(new TopicRef(shortName, "topic", ProtocolType.PULSAR));
                }
            }
        } catch (Exception e) {
            refs.add(new TopicRef(
                    profile.propertyOrDefault("topic", "persistent://public/default/eventore"),
                    "topic",
                    ProtocolType.PULSAR));
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        try {
            PulsarClient client = buildClient(profile);
            String subscription = request.getConsumerGroup() != null
                    ? request.getConsumerGroup()
                    : "eventore-sub";
            List<String> topicNames = SubscribeDestinations.resolve(request).stream()
                    .map(this::normalizeTopic)
                    .toList();
            Consumer<byte[]> consumer = client.newConsumer(Schema.BYTES)
                    .topics(topicNames)
                    .subscriptionName(subscription)
                    .subscriptionType(SubscriptionType.Shared)
                    .messageListener((c, msg) -> {
                        UnifiedMessage unified = new UnifiedMessage();
                        unified.setConnectionId(profile.getId());
                        unified.setProtocol(ProtocolType.PULSAR);
                        unified.setDestination(msg.getTopicName());
                        unified.setDirection(MessageDirection.INBOUND);
                        unified.setPayload(new String(msg.getData(), StandardCharsets.UTF_8));
                        unified.getHeaders().put("messageId", msg.getMessageId().toString());
                        handler.onMessage(unified);
                    })
                    .subscribe();
            String key = request.getSubscriptionKey() != null
                    ? request.getSubscriptionKey()
                    : profile.getId() + ":" + String.join(",", topicNames);
            closeClient(key);
            clients.put(key, client);
            return () -> {
                try {
                    consumer.close();
                    client.close();
                } finally {
                    clients.remove(key);
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (PulsarClient client = buildClient(profile)) {
            String topic = normalizeTopic(request.getDestination());
            var producer = client.newProducer(Schema.BYTES).topic(topic).create();
            try {
                byte[] data = request.getPayload() != null
                        ? request.getPayload().getBytes(StandardCharsets.UTF_8)
                        : new byte[0];
                producer.send(data);
            } finally {
                producer.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar publish failed: " + e.getMessage(), e);
        }
    }

    private void closeClient(String key) {
        PulsarClient existing = clients.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    @Override
    public void close(String connectionId) {
        clients.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(connectionId)) {
                try {
                    entry.getValue().close();
                } catch (Exception ignored) {
                    // ignore
                }
                return true;
            }
            return false;
        });
    }

    private String normalizeTopic(String destination) {
        if (destination.startsWith("persistent://") || destination.startsWith("non-persistent://")) {
            return destination;
        }
        return "persistent://public/default/" + destination;
    }

    private PulsarClient buildClient(ConnectionProfile profile) throws Exception {
        return PulsarClient.builder().serviceUrl(profile.getBrokerUrl()).build();
    }

    private PulsarAdmin buildAdmin(ConnectionProfile profile) throws Exception {
        return PulsarAdmin.builder().serviceHttpUrl(httpUrl(profile)).build();
    }

    private String httpUrl(ConnectionProfile profile) {
        String url = profile.getBrokerUrl();
        if (url.startsWith("pulsar://")) {
            return url.replace("pulsar://", "http://");
        }
        return profile.propertyOrDefault("adminUrl", "http://" + url.replace(":6650", ":8080"));
    }
}

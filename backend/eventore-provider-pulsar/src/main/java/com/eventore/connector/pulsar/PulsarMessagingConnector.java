package com.eventore.connector.pulsar;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.connector.spi.SubscriptionKeys;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PulsarMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(PulsarMessagingConnector.class);

    private final Map<String, PulsarClient> clients = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.PULSAR;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (PulsarClient client = buildClient(profile)) {
            client.getPartitionsForTopic("persistent://" + tenant(profile) + "/default/test")
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // topic may not exist; admin list is enough
            try (PulsarAdmin admin = buildAdmin(profile)) {
                admin.namespaces().getNamespaces(tenant(profile));
            } catch (Exception ex) {
                throw new IllegalStateException("Pulsar connection failed: " + ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        String tenant = tenant(profile);
        try (PulsarAdmin admin = buildAdmin(profile)) {
            for (String ns : admin.namespaces().getNamespaces(tenant)) {
                for (String topic : admin.topics().getList(ns)) {
                    String shortName = topic.contains("/") ? topic.substring(topic.lastIndexOf('/') + 1) : topic;
                    refs.add(new TopicRef(shortName, "topic", ProtocolType.PULSAR));
                }
            }
        } catch (Exception e) {
            log.debug("Pulsar admin topic listing failed for connection {}; returning configured default topic",
                    profile.getId(), e);
            refs.add(new TopicRef(
                    profile.propertyOrDefault("topic", "persistent://" + tenant + "/default/eventore"),
                    "topic",
                    ProtocolType.PULSAR));
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        final PulsarClient client;
        try {
            client = buildClient(profile);
        } catch (Exception e) {
            throw new IllegalStateException("Pulsar subscribe failed: " + e.getMessage(), e);
        }
        try {
            // Default to a per-session subscription name: a shared static default would
            // make Pulsar distribute messages across unrelated subscribe sessions in
            // Shared mode. An explicitly configured consumer group is still honored.
            String subscription = request.getConsumerGroup() != null
                    ? request.getConsumerGroup()
                    : "eventore-sub-" + UUID.randomUUID();
            List<String> topicNames = SubscribeDestinations.resolve(request).stream()
                    .map(dest -> normalizeTopic(profile, dest))
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
                        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(msg.getData());
                        unified.setPayload(decoded.text());
                        unified.setContentType(decoded.contentType());
                        unified.putHeader("messageId", msg.getMessageId().toString());
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
            // Consumer setup failed after the client was built; close it so it does not leak.
            try {
                client.close();
            } catch (Exception closeError) {
                log.debug("Error closing Pulsar client after failed subscribe", closeError);
            }
            throw new IllegalStateException("Pulsar subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (PulsarClient client = buildClient(profile)) {
            String topic = normalizeTopic(profile, request.getDestination());
            var producer = client.newProducer(Schema.BYTES).topic(topic).create();
            try {
                byte[] data = PayloadCodec.toBytes(request.getPayload(), request.getContentType());
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
            } catch (Exception e) {
                log.debug("Error closing previous Pulsar client for subscription key {}", key, e);
            }
        }
    }

    @Override
    public void close(String connectionId) {
        clients.entrySet().removeIf(entry -> {
            if (SubscriptionKeys.belongsToConnection(entry.getKey(), connectionId)) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.debug("Error closing Pulsar client {} while closing connection {}",
                            entry.getKey(), connectionId, e);
                }
                return true;
            }
            return false;
        });
    }

    private String normalizeTopic(ConnectionProfile profile, String destination) {
        if (destination.startsWith("persistent://") || destination.startsWith("non-persistent://")) {
            return destination;
        }
        return "persistent://" + tenant(profile) + "/default/" + destination;
    }

    private String tenant(ConnectionProfile profile) {
        return profile.propertyOrDefault("tenant", "public");
    }

    private PulsarClient buildClient(ConnectionProfile profile) throws Exception {
        var builder = PulsarClient.builder().serviceUrl(profile.getBrokerUrl());
        applyClientAuthAndTls(profile, builder);
        return builder.build();
    }

    private PulsarAdmin buildAdmin(ConnectionProfile profile) throws Exception {
        var builder = PulsarAdmin.builder().serviceHttpUrl(httpUrl(profile));
        String authPlugin = profile.property("authPluginClassName");
        if (authPlugin != null && !authPlugin.isBlank()) {
            String authParams = profile.propertyOrDefault("authParams", "");
            builder.authentication(authPlugin, authParams);
        }
        String tlsTrustCerts = profile.property("tlsTrustCertsFilePath");
        if (tlsTrustCerts != null && !tlsTrustCerts.isBlank()) {
            builder.tlsTrustCertsFilePath(tlsTrustCerts);
        }
        return builder.build();
    }

    private static void applyClientAuthAndTls(ConnectionProfile profile, ClientBuilder builder) throws Exception {
        String authPlugin = profile.property("authPluginClassName");
        if (authPlugin != null && !authPlugin.isBlank()) {
            String authParams = profile.propertyOrDefault("authParams", "");
            builder.authentication(authPlugin, authParams);
        }
        String tlsTrustCerts = profile.property("tlsTrustCertsFilePath");
        if (tlsTrustCerts != null && !tlsTrustCerts.isBlank()) {
            builder.tlsTrustCertsFilePath(tlsTrustCerts);
        }
    }

    private String httpUrl(ConnectionProfile profile) {
        // An explicitly configured adminUrl always wins over the derived fallback.
        String adminUrl = profile.property("adminUrl");
        if (adminUrl != null && !adminUrl.isBlank()) {
            return adminUrl;
        }
        String url = profile.getBrokerUrl();
        if (url.startsWith("pulsar://")) {
            return url.replace("pulsar://", "http://");
        }
        return "http://" + url.replace(":6650", ":8080");
    }
}

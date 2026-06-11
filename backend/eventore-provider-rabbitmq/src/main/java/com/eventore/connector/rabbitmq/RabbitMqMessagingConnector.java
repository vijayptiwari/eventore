package com.eventore.connector.rabbitmq;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.connector.spi.SubscriptionKeys;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessagingConnector.class);

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.RABBITMQ;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (Connection conn = factory(profile).newConnection()) {
            conn.createChannel().close();
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        // The connection/channel is opened purely to validate broker reachability so
        // callers get a connection error instead of silently static defaults.
        try (Connection conn = factory(profile).newConnection();
                Channel ch = conn.createChannel()) {
            refs.add(new TopicRef(
                    profile.propertyOrDefault("queue", "eventore.queue"),
                    "queue",
                    ProtocolType.RABBITMQ));
            String exchange = profile.property("exchange");
            if (exchange != null) {
                refs.add(new TopicRef(exchange, "exchange", ProtocolType.RABBITMQ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list RabbitMQ destinations: " + e.getMessage(), e);
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        final Connection conn;
        try {
            conn = factory(profile).newConnection();
        } catch (Exception e) {
            log.warn("RabbitMQ subscribe failed for connection {}", profile.getId(), e);
            throw new IllegalStateException("RabbitMQ subscribe failed: " + e.getMessage(), e);
        }
        try {
            Channel channel = conn.createChannel();
            String queue = request.getDestination();
            channel.queueDeclare(queue, true, false, false, null);
            String tag = channel.basicConsume(
                    queue,
                    true,
                    (DeliverCallback) (consumerTag, delivery) -> {
                        UnifiedMessage msg = new UnifiedMessage();
                        msg.setConnectionId(profile.getId());
                        msg.setProtocol(ProtocolType.RABBITMQ);
                        msg.setDestination(queue);
                        msg.setDirection(MessageDirection.INBOUND);
                        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(delivery.getBody());
                        msg.setPayload(decoded.text());
                        msg.setContentType(decoded.contentType());
                        msg.putHeader("routingKey", delivery.getEnvelope().getRoutingKey());
                        handler.onMessage(msg);
                    },
                    consumerTag -> {});
            String key = request.getSubscriptionKey() != null
                    ? request.getSubscriptionKey()
                    : profile.getId() + ":" + queue;
            closeKey(key);
            connections.put(key, conn);
            return () -> {
                try {
                    channel.basicCancel(tag);
                    channel.close();
                    conn.close();
                } finally {
                    connections.remove(key);
                }
            };
        } catch (Exception e) {
            // Setup failed after the connection opened; close it so it does not leak.
            try {
                conn.close();
            } catch (Exception closeError) {
                log.debug("Error closing RabbitMQ connection after failed subscribe", closeError);
            }
            log.warn("RabbitMQ subscribe failed for connection {} queue {}",
                    profile.getId(), request.getDestination(), e);
            throw new IllegalStateException("RabbitMQ subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (Connection conn = factory(profile).newConnection();
                Channel channel = conn.createChannel()) {
            Map<String, String> headers = Optional.ofNullable(request.getHeaders()).orElseGet(Map::of);
            String exchange = headers.getOrDefault("exchange", "");
            String routingKey = headers.getOrDefault("routingKey", request.getDestination());
            channel.basicPublish(
                    exchange,
                    routingKey,
                    null,
                    PayloadCodec.toBytes(request.getPayload(), request.getContentType()));
        } catch (Exception e) {
            log.warn("RabbitMQ publish failed for connection {} destination {}",
                    profile.getId(), request.getDestination(), e);
            throw new IllegalStateException("RabbitMQ publish failed: " + e.getMessage(), e);
        }
    }

    private void closeKey(String key) {
        Connection existing = connections.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception e) {
                log.debug("Error closing previous RabbitMQ connection for subscription key {}", key, e);
            }
        }
    }

    @Override
    public void close(String connectionId) {
        connections.entrySet().removeIf(entry -> {
            if (SubscriptionKeys.belongsToConnection(entry.getKey(), connectionId)) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.debug("Error closing RabbitMQ connection {} while closing connection {}",
                            entry.getKey(), connectionId, e);
                }
                return true;
            }
            return false;
        });
    }

    private ConnectionFactory factory(ConnectionProfile profile) {
        ConnectionFactory factory = new ConnectionFactory();
        RabbitMqBrokerUrls.Endpoint endpoint = RabbitMqBrokerUrls.parse(profile.getBrokerUrl());
        factory.setHost(endpoint.host());
        factory.setPort(endpoint.port());
        if (endpoint.tls()) {
            try {
                factory.useSslProtocol();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to enable TLS for RabbitMQ: " + e.getMessage(), e);
            }
        }
        factory.setVirtualHost(profile.propertyOrDefault("vhost", "/"));
        String username = profile.credential("username");
        String password = profile.credential("password");
        if (username != null) {
            factory.setUsername(username);
        }
        if (password != null) {
            factory.setPassword(password);
        }
        return factory;
    }
}

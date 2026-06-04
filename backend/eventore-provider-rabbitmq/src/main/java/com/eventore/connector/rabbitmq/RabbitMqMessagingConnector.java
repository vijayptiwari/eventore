package com.eventore.connector.rabbitmq;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqMessagingConnector implements MessagingConnector {

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
        try {
            Connection conn = factory(profile).newConnection();
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
                        msg.setPayload(new String(delivery.getBody(), StandardCharsets.UTF_8));
                        msg.getHeaders().put("routingKey", delivery.getEnvelope().getRoutingKey());
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
            throw new IllegalStateException("RabbitMQ subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (Connection conn = factory(profile).newConnection();
                Channel channel = conn.createChannel()) {
            String exchange = request.getHeaders().getOrDefault("exchange", "");
            String routingKey = request.getHeaders().getOrDefault("routingKey", request.getDestination());
            channel.basicPublish(
                    exchange,
                    routingKey,
                    null,
                    request.getPayload().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ publish failed: " + e.getMessage(), e);
        }
    }

    private void closeKey(String key) {
        Connection existing = connections.remove(key);
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
        connections.entrySet().removeIf(entry -> {
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

    private ConnectionFactory factory(ConnectionProfile profile) {
        ConnectionFactory factory = new ConnectionFactory();
        String[] parts = profile.getBrokerUrl().split(":");
        factory.setHost(parts[0]);
        if (parts.length > 1) {
            factory.setPort(Integer.parseInt(parts[1]));
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

package com.eventore.connector.jms;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JmsMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(JmsMessagingConnector.class);

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.JMS;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (Connection conn = factory(profile).createConnection()) {
            conn.start();
        } catch (Exception e) {
            throw new IllegalStateException("JMS connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        String queue = profile.propertyOrDefault("queue", "eventore.queue");
        refs.add(new TopicRef(queue, "queue", ProtocolType.JMS));
        String topic = profile.property("topic");
        if (topic != null) {
            refs.add(new TopicRef(topic, "topic", ProtocolType.JMS));
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        final Connection conn;
        try {
            conn = factory(profile).createConnection();
        } catch (Exception e) {
            throw new IllegalStateException("JMS subscribe failed: " + e.getMessage(), e);
        }
        try {
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String destType = Optional.ofNullable(request.getOptions()).orElseGet(Map::of)
                    .getOrDefault("destinationType", "queue");
            MessageConsumer consumer;
            if ("topic".equalsIgnoreCase(destType)) {
                consumer = session.createConsumer(session.createTopic(request.getDestination()));
            } else {
                consumer = session.createConsumer(session.createQueue(request.getDestination()));
            }
            consumer.setMessageListener(message -> {
                UnifiedMessage msg = new UnifiedMessage();
                msg.setConnectionId(profile.getId());
                msg.setProtocol(ProtocolType.JMS);
                msg.setDestination(request.getDestination());
                msg.setDirection(MessageDirection.INBOUND);
                try {
                    if (message instanceof TextMessage textMessage) {
                        msg.setPayload(textMessage.getText());
                    } else if (message instanceof BytesMessage bytesMessage) {
                        byte[] body = new byte[(int) bytesMessage.getBodyLength()];
                        bytesMessage.readBytes(body);
                        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(body);
                        msg.setPayload(decoded.text());
                        msg.setContentType(decoded.contentType());
                    } else {
                        msg.setPayload(message.toString());
                    }
                } catch (JMSException e) {
                    handler.onError(e.getMessage());
                    return;
                }
                handler.onMessage(msg);
            });
            String key = request.getSubscriptionKey() != null
                    ? request.getSubscriptionKey()
                    : profile.getId() + ":" + request.getDestination();
            closeConnection(key);
            connections.put(key, conn);
            return () -> {
                try {
                    consumer.close();
                    session.close();
                    conn.close();
                } finally {
                    connections.remove(key);
                }
            };
        } catch (Exception e) {
            // Session/consumer setup failed after the connection opened; close it so
            // it does not leak.
            try {
                conn.close();
            } catch (Exception closeError) {
                log.debug("Error closing JMS connection after failed subscribe", closeError);
            }
            throw new IllegalStateException("JMS subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (Connection conn = factory(profile).createConnection()) {
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String destType = Optional.ofNullable(request.getHeaders()).orElseGet(Map::of)
                    .getOrDefault("destinationType", "queue");
            Message message;
            if (PayloadCodec.isBase64(request.getContentType())) {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(PayloadCodec.toBytes(request.getPayload(), request.getContentType()));
                message = bytesMessage;
            } else {
                message = session.createTextMessage(request.getPayload() != null ? request.getPayload() : "");
            }
            if ("topic".equalsIgnoreCase(destType)) {
                session.createProducer(session.createTopic(request.getDestination())).send(message);
            } else {
                session.createProducer(session.createQueue(request.getDestination())).send(message);
            }
            session.close();
        } catch (Exception e) {
            throw new IllegalStateException("JMS publish failed: " + e.getMessage(), e);
        }
    }

    private void closeConnection(String key) {
        Connection existing = connections.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception e) {
                log.debug("Error closing previous JMS connection for subscription key {}", key, e);
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
                    log.debug("Error closing JMS connection {} while closing connection {}",
                            entry.getKey(), connectionId, e);
                }
                return true;
            }
            return false;
        });
    }

    private ActiveMQConnectionFactory factory(ConnectionProfile profile) {
        String brokerUrl = profile.getBrokerUrl();
        if (!brokerUrl.startsWith("tcp://")) {
            brokerUrl = "tcp://" + brokerUrl;
        }
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        String username = profile.credential("username");
        String password = profile.credential("password");
        if (username != null) {
            factory.setUser(username);
        }
        if (password != null) {
            factory.setPassword(password);
        }
        return factory;
    }
}

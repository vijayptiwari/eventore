package com.eventore.connector.jms;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class JmsMessagingConnector implements MessagingConnector {

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
        try {
            Connection conn = factory(profile).createConnection();
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String destType = request.getOptions().getOrDefault("destinationType", "queue");
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
            throw new IllegalStateException("JMS subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (Connection conn = factory(profile).createConnection()) {
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String destType = request.getHeaders().getOrDefault("destinationType", "queue");
            TextMessage message = session.createTextMessage(request.getPayload());
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

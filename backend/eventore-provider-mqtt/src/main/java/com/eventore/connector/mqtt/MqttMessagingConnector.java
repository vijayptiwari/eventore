package com.eventore.connector.mqtt;

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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(MqttMessagingConnector.class);

    private final Map<String, MqttClient> clients = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.MQTT;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (MqttClient client = createClient(profile, "eventore-validate-" + randomSuffix())) {
            client.connect(options(profile));
            client.disconnect();
        } catch (Exception e) {
            throw new IllegalStateException("MQTT connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        String filter = profile.propertyOrDefault("topicFilter", "#");
        List<TopicRef> topics = new ArrayList<>();
        topics.add(new TopicRef(filter, "topic-filter", ProtocolType.MQTT));
        return topics;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String key = request.getSubscriptionKey() != null
                ? request.getSubscriptionKey()
                : profile.getId() + ":" + request.getDestination();
        closeClient(key);
        final MqttClient client;
        try {
            client = createClient(profile, "eventore-sub-" + key.replace(":", "-"));
        } catch (Exception e) {
            throw new IllegalStateException("MQTT subscribe failed: " + e.getMessage(), e);
        }
        try {
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    handler.onError(cause != null ? cause.getMessage() : "connection lost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    UnifiedMessage msg = new UnifiedMessage();
                    msg.setConnectionId(profile.getId());
                    msg.setProtocol(ProtocolType.MQTT);
                    msg.setDestination(topic);
                    msg.setDirection(MessageDirection.INBOUND);
                    PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(message.getPayload());
                    msg.setPayload(decoded.text());
                    msg.setContentType(decoded.contentType());
                    msg.putHeader("qos", String.valueOf(message.getQos()));
                    handler.onMessage(msg);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            client.connect(options(profile));
            int qos = parseQos(
                    java.util.Optional.ofNullable(request.getOptions()).orElseGet(Map::of).get("qos"),
                    "subscribe");
            List<String> topics = SubscribeDestinations.resolve(request);
            String[] topicArr = topics.toArray(String[]::new);
            int[] qosArr = new int[topicArr.length];
            java.util.Arrays.fill(qosArr, qos);
            client.subscribe(topicArr, qosArr);
            clients.put(key, client);
            return () -> {
                try {
                    client.unsubscribe(topicArr);
                    client.disconnect();
                    client.close();
                } finally {
                    clients.remove(key);
                }
            };
        } catch (Exception e) {
            // Setup failed after the client was created; release it so it does not leak.
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception closeError) {
                log.debug("Error closing MQTT client after failed subscribe", closeError);
            }
            throw new IllegalStateException("MQTT subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        // Each publish opens a short-lived client so concurrent publishes never share connection state.
        try (MqttClient client = createClient(profile, "eventore-pub-" + randomSuffix())) {
            client.connect(options(profile));
            MqttMessage message = new MqttMessage(
                    PayloadCodec.toBytes(request.getPayload(), request.getContentType()));
            message.setQos(parseQos(
                    java.util.Optional.ofNullable(request.getHeaders()).orElseGet(Map::of).get("qos"),
                    "publish"));
            client.publish(request.getDestination(), message);
            client.disconnect();
        } catch (Exception e) {
            throw new IllegalStateException("MQTT publish failed: " + e.getMessage(), e);
        }
    }

    private void closeClient(String key) {
        MqttClient existing = clients.remove(key);
        if (existing != null) {
            try {
                if (existing.isConnected()) {
                    existing.disconnect();
                }
                existing.close();
            } catch (Exception e) {
                log.debug("Error closing previous MQTT client for subscription key {}", key, e);
            }
        }
    }

    @Override
    public void close(String connectionId) {
        clients.entrySet().removeIf(entry -> {
            if (SubscriptionKeys.belongsToConnection(entry.getKey(), connectionId)) {
                try {
                    MqttClient c = entry.getValue();
                    if (c.isConnected()) {
                        c.disconnect();
                    }
                    c.close();
                } catch (Exception e) {
                    log.debug("Error closing MQTT client {} while closing connection {}",
                            entry.getKey(), connectionId, e);
                }
                return true;
            }
            return false;
        });
    }

    /** Short random id segment so concurrent validate/publish clients never collide. */
    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private MqttClient createClient(ConnectionProfile profile, String clientId) throws Exception {
        String serverUri = profile.getBrokerUrl();
        if (!serverUri.startsWith("tcp://") && !serverUri.startsWith("ssl://")) {
            serverUri = "tcp://" + serverUri;
        }
        return new MqttClient(serverUri, clientId, new MemoryPersistence());
    }

    private MqttConnectOptions options(ConnectionProfile profile) {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        String username = profile.credential("username");
        String password = profile.credential("password");
        if (username != null) {
            opts.setUserName(username);
        }
        if (password != null) {
            opts.setPassword(password.toCharArray());
        }
        return opts;
    }

    private static int parseQos(String raw, String context) {
        String value = raw != null && !raw.isBlank() ? raw : "1";
        try {
            int qos = Integer.parseInt(value);
            if (qos < 0 || qos > 2) {
                throw new IllegalArgumentException(
                        "MQTT " + context + " qos must be 0, 1, or 2, was: " + value);
            }
            return qos;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "MQTT " + context + " qos must be an integer, was: " + value, e);
        }
    }
}

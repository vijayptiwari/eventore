package com.eventore.connector.mqtt;

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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

@Component
public class MqttMessagingConnector implements MessagingConnector {

    private final Map<String, MqttClient> clients = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.MQTT;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (MqttClient client = createClient(profile, "eventore-validate")) {
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
        try {
            String key = request.getSubscriptionKey() != null
                    ? request.getSubscriptionKey()
                    : profile.getId() + ":" + request.getDestination();
            closeClient(key);
            MqttClient client = createClient(profile, "eventore-sub-" + key.replace(":", "-"));
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
                    msg.setPayload(new String(message.getPayload(), StandardCharsets.UTF_8));
                    msg.getHeaders().put("qos", String.valueOf(message.getQos()));
                    handler.onMessage(msg);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            client.connect(options(profile));
            int qos = Integer.parseInt(request.getOptions().getOrDefault("qos", "1"));
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
            throw new IllegalStateException("MQTT subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (MqttClient client = createClient(profile, "eventore-pub")) {
            client.connect(options(profile));
            MqttMessage message = new MqttMessage(
                    (request.getPayload() != null ? request.getPayload() : "")
                            .getBytes(StandardCharsets.UTF_8));
            message.setQos(Integer.parseInt(
                    request.getHeaders().getOrDefault("qos", "1")));
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
                    MqttClient c = entry.getValue();
                    if (c.isConnected()) {
                        c.disconnect();
                    }
                    c.close();
                } catch (Exception ignored) {
                    // ignore
                }
                return true;
            }
            return false;
        });
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
}

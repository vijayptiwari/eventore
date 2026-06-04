package com.eventore.connector.kafka;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessagingConnector implements MessagingConnector {

    private final Map<String, AutoCloseable> activeConsumers = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.KAFKA;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            admin.listTopics().names().get();
        } catch (Exception e) {
            throw new IllegalStateException("Kafka connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            ListTopicsResult result = admin.listTopics();
            List<TopicRef> topics = new ArrayList<>();
            for (String name : result.names().get()) {
                topics.add(new TopicRef(name, "topic", ProtocolType.KAFKA));
            }
            topics.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return topics;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Kafka topics: " + e.getMessage(), e);
        }
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String group = request.getConsumerGroup() != null
                ? request.getConsumerGroup()
                : "eventore-" + UUID.randomUUID();
        String key = request.getSubscriptionKey() != null
                ? request.getSubscriptionKey()
                : profile.getId() + ":" + request.getDestination();
        closeExisting(key);
        Properties props = KafkaClientSupport.consumerProps(profile, group);
        List<String> topics = SubscribeDestinations.resolve(request);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-sub-" + key);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> pollLoop(profile, handler, consumer, running));
        AutoCloseable closeable = () -> {
            running.set(false);
            consumer.wakeup();
            consumer.close(Duration.ofSeconds(5));
            executor.shutdownNow();
            activeConsumers.remove(key);
        };
        activeConsumers.put(key, closeable);
        return closeable;
    }

    private void closeExisting(String key) {
        AutoCloseable existing = activeConsumers.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private void pollLoop(
            ConnectionProfile profile,
            MessageHandler handler,
            KafkaConsumer<String, byte[]> consumer,
            AtomicBoolean running) {
        try {
            while (running.get()) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    UnifiedMessage msg = new UnifiedMessage();
                    msg.setConnectionId(profile.getId());
                    msg.setProtocol(ProtocolType.KAFKA);
                    msg.setDestination(record.topic());
                    msg.setDirection(MessageDirection.INBOUND);
                    msg.setPayload(record.value() != null
                            ? new String(record.value(), StandardCharsets.UTF_8)
                            : "");
                    msg.getHeaders().put("partition", String.valueOf(record.partition()));
                    msg.getHeaders().put("offset", String.valueOf(record.offset()));
                    if (record.key() != null) {
                        msg.getHeaders().put("key", record.key());
                    }
                    for (Header header : record.headers()) {
                        if (header.key() != null && header.value() != null) {
                            msg.getHeaders()
                                    .put(
                                            header.key(),
                                            new String(header.value(), StandardCharsets.UTF_8));
                        }
                    }
                    handler.onMessage(msg);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                handler.onError(e.getMessage());
            }
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        Properties props = KafkaClientSupport.producerProps(profile);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            byte[] bytes = request.getPayload() != null
                    ? request.getPayload().getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            String key = request.getHeaders() != null ? request.getHeaders().get("key") : null;
            Integer partition = parsePartitionHeader(request);
            ProducerRecord<String, byte[]> record = partition != null
                    ? new ProducerRecord<>(request.getDestination(), partition, key, bytes)
                    : new ProducerRecord<>(request.getDestination(), key, bytes);
            if (request.getHeaders() != null) {
                request.getHeaders().forEach((k, v) -> {
                    if (v != null && !"key".equals(k) && !"partition".equals(k) && !"flush".equals(k)) {
                        record.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
                    }
                });
            }
            producer.send(record).get();
            if (request.getHeaders() != null
                    && "true".equalsIgnoreCase(request.getHeaders().get("flush"))) {
                producer.flush();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish failed: " + e.getMessage(), e);
        }
    }

    private static Integer parsePartitionHeader(PublishRequest request) {
        if (request.getHeaders() == null) {
            return null;
        }
        String p = request.getHeaders().get("partition");
        if (p == null || p.isBlank()) {
            return null;
        }
        return Integer.parseInt(p);
    }

    @Override
    public void close(String connectionId) {
        activeConsumers.entrySet().removeIf(entry -> {
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
}

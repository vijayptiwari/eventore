package com.eventore.connector.kafka;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingConnector.class);

    private final Map<String, AutoCloseable> activeConsumers = new ConcurrentHashMap<>();
    private final Map<String, KafkaProducer<String, byte[]>> producers = new ConcurrentHashMap<>();

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
        AtomicBoolean running = new AtomicBoolean(true);
        // The consumer is created and closed inside the poll thread: KafkaConsumer is
        // not thread-safe, so the closeable only signals shutdown via wakeup().
        AtomicReference<KafkaConsumer<String, byte[]>> consumerRef = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-sub-" + key);
            t.setDaemon(true);
            return t;
        });
        AutoCloseable closeable = () -> {
            running.set(false);
            KafkaConsumer<String, byte[]> consumer = consumerRef.get();
            if (consumer != null) {
                consumer.wakeup();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
            activeConsumers.remove(key);
        };
        executor.submit(() -> runSubscription(profile, handler, props, topics, running, consumerRef));
        activeConsumers.put(key, closeable);
        return closeable;
    }

    private void runSubscription(
            ConnectionProfile profile,
            MessageHandler handler,
            Properties props,
            List<String> topics,
            AtomicBoolean running,
            AtomicReference<KafkaConsumer<String, byte[]>> consumerRef) {
        if (!running.get()) {
            return;
        }
        try (KafkaConsumer<String, byte[]> consumer = newConsumer(props)) {
            consumerRef.set(consumer);
            consumer.subscribe(topics);
            pollLoop(profile, handler, consumer, running);
        } catch (Exception e) {
            if (running.get()) {
                log.warn("Kafka subscription failed for connection {}", profile.getId(), e);
                handler.onError(e.getMessage());
            }
        }
    }

    KafkaConsumer<String, byte[]> newConsumer(Properties props) {
        return new KafkaConsumer<>(props);
    }

    private void closeExisting(String key) {
        AutoCloseable existing = activeConsumers.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception e) {
                log.debug("Error closing previous Kafka consumer for subscription key {}", key, e);
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
                    PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(record.value());
                    msg.setPayload(decoded.text());
                    msg.setContentType(decoded.contentType());
                    msg.putHeader("partition", String.valueOf(record.partition()));
                    msg.putHeader("offset", String.valueOf(record.offset()));
                    if (record.key() != null) {
                        msg.putHeader("key", record.key());
                    }
                    for (Header header : record.headers()) {
                        if (header.key() != null && header.value() != null) {
                            msg.putHeader(header.key(), PayloadCodec.fromBytes(header.value()).text());
                        }
                    }
                    handler.onMessage(msg);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.warn("Kafka poll loop failed for connection {}", profile.getId(), e);
                handler.onError(e.getMessage());
            }
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        // KafkaProducer is thread-safe; one cached instance per connection profile,
        // closed in close(connectionId).
        KafkaProducer<String, byte[]> producer = producers.computeIfAbsent(
                profile.getId(),
                id -> new KafkaProducer<>(KafkaClientSupport.producerProps(profile)));
        try {
            byte[] bytes = PayloadCodec.toBytes(request.getPayload(), request.getContentType());
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
            log.warn("Kafka publish failed for connection {} destination {}",
                    profile.getId(), request.getDestination(), e);
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
        try {
            return Integer.parseInt(p.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid partition header: " + p);
        }
    }

    @Override
    public void close(String connectionId) {
        KafkaProducer<String, byte[]> producer = producers.remove(connectionId);
        if (producer != null) {
            try {
                producer.close(Duration.ofSeconds(5));
            } catch (Exception e) {
                log.debug("Error closing Kafka producer for connection {}", connectionId, e);
            }
        }
        activeConsumers.entrySet().removeIf(entry -> {
            if (SubscriptionKeys.belongsToConnection(entry.getKey(), connectionId)) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.debug("Error closing Kafka consumer {} while closing connection {}",
                            entry.getKey(), connectionId, e);
                }
                return true;
            }
            return false;
        });
    }
}

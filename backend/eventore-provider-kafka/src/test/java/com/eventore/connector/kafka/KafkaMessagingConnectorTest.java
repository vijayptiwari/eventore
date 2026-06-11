package com.eventore.connector.kafka;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaMessagingConnectorTest {

    private KafkaMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new KafkaMessagingConnector();
    }

    @AfterEach
    void tearDown() {
        connector.close("test-kafka");
    }

    @Test
    void protocolIsKafka() {
        assertEquals(ProtocolType.KAFKA, connector.protocol());
    }

    @Test
    void closeWithNoActiveConsumersDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-1"));
    }

    @Test
    void publishRejectsInvalidBase64Payload() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        PublishRequest request = new PublishRequest();
        request.setDestination("topic-a");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("Kafka publish failed"));
    }

    @Test
    void publishRejectsNonNumericPartitionHeader() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        PublishRequest request = new PublishRequest();
        request.setDestination("topic-a");
        request.setPayload("hello");
        request.setHeaders(Map.of("partition", "not-a-number"));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Invalid partition header"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void subscribeCloseWakeupConsumerOnShutdown() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("topic-a");
        CountDownLatch consumerCreated = new CountDownLatch(1);
        AtomicReference<KafkaConsumer<String, byte[]>> consumerRef = new AtomicReference<>();
        KafkaMessagingConnector testConnector = new KafkaMessagingConnector() {
            @Override
            KafkaConsumer<String, byte[]> newConsumer(Properties props) {
                KafkaConsumer<String, byte[]> consumer = mock(KafkaConsumer.class);
                when(consumer.poll(any(Duration.class))).thenAnswer(inv -> {
                    consumerCreated.countDown();
                    Thread.sleep(200);
                    return ConsumerRecords.empty();
                });
                consumerRef.set(consumer);
                return consumer;
            }
        };

        AutoCloseable subscription = testConnector.subscribe(profile, request, msg -> {});
        assertTrue(consumerCreated.await(5, TimeUnit.SECONDS));
        subscription.close();

        verify(consumerRef.get()).wakeup();
        testConnector.close(profile.getId());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishReusesCachedProducerPerConnection() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        PublishRequest request = new PublishRequest();
        request.setDestination("topic-a");
        request.setPayload("hello");
        Future<?> sendFuture = mock(Future.class);
        when(sendFuture.get()).thenReturn(null);

        try (MockedConstruction<KafkaProducer> producers = mockConstruction(
                KafkaProducer.class,
                (mock, ctx) -> when(mock.send(any(ProducerRecord.class))).thenReturn(sendFuture))) {
            connector.publish(profile, request);
            connector.publish(profile, request);
            assertEquals(1, producers.constructed().size());

            connector.close(profile.getId());
            verify(producers.constructed().get(0)).close(any(Duration.class));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void subscribeReportsErrorWhenConsumerSetupFails() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("topic-a");
        CountDownLatch errorReceived = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        KafkaMessagingConnector testConnector = new KafkaMessagingConnector() {
            @Override
            KafkaConsumer<String, byte[]> newConsumer(Properties props) {
                KafkaConsumer<String, byte[]> consumer = mock(KafkaConsumer.class);
                doAnswer(inv -> {
                    throw new RuntimeException("subscribe failed");
                }).when(consumer).subscribe(any(java.util.Collection.class));
                return consumer;
            }
        };

        AutoCloseable subscription = testConnector.subscribe(profile, request, new com.eventore.connector.spi.MessageHandler() {
            @Override
            public void onMessage(com.eventore.domain.UnifiedMessage message) {}

            @Override
            public void onError(String message) {
                error.set(message);
                errorReceived.countDown();
            }
        });
        assertTrue(errorReceived.await(5, TimeUnit.SECONDS));
        subscription.close();
        assertTrue(error.get().contains("subscribe failed"));
        testConnector.close(profile.getId());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void closeRemovesActiveSubscriptionEntry() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("topic-a");
        CountDownLatch consumerCreated = new CountDownLatch(1);
        AtomicReference<KafkaConsumer<String, byte[]>> consumerRef = new AtomicReference<>();
        KafkaMessagingConnector testConnector = new KafkaMessagingConnector() {
            @Override
            KafkaConsumer<String, byte[]> newConsumer(Properties props) {
                KafkaConsumer<String, byte[]> consumer = mock(KafkaConsumer.class);
                when(consumer.poll(any(Duration.class))).thenAnswer(inv -> {
                    consumerCreated.countDown();
                    Thread.sleep(500);
                    return ConsumerRecords.empty();
                });
                consumerRef.set(consumer);
                return consumer;
            }
        };

        AutoCloseable subscription = testConnector.subscribe(profile, request, msg -> {});
        assertTrue(consumerCreated.await(5, TimeUnit.SECONDS));
        subscription.close();
        verify(consumerRef.get()).wakeup();
        assertDoesNotThrow(() -> testConnector.close(profile.getId()));
    }
}

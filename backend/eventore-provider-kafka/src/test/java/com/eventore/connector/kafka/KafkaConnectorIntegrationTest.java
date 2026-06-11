package com.eventore.connector.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Broker round-trip tests against a real Kafka started by Testcontainers.
 * Automatically skipped when Docker is not available on the host.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KafkaConnectorIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    private final KafkaMessagingConnector connector = new KafkaMessagingConnector();

    private ConnectionProfile profile() {
        return StreamTestFixtures.profile(ProtocolType.KAFKA, KAFKA.getBootstrapServers());
    }

    @Test
    void validateSucceedsAgainstLiveBroker() {
        connector.validate(profile());
    }

    @Test
    void publishSubscribeTextRoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String topic = "it-text-" + UUID.randomUUID();

        PublishRequest publish = new PublishRequest();
        publish.setDestination(topic);
        publish.setPayload("hello-integration");
        connector.publish(profile, publish);

        List<UnifiedMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        SubscribeRequest subscribe = new SubscribeRequest();
        subscribe.setDestination(topic);
        AutoCloseable subscription = connector.subscribe(profile, subscribe, new MessageHandler() {
            @Override
            public void onMessage(UnifiedMessage message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onError(String error) {}
        });
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        assertThat(received.get(0).getPayload()).isEqualTo("hello-integration");
        assertThat(received.get(0).getDestination()).isEqualTo(topic);
    }

    @Test
    void binaryPayloadSurvivesBase64RoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String topic = "it-binary-" + UUID.randomUUID();
        byte[] binary = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x10, (byte) 0x80, 0x7F};

        PublishRequest publish = new PublishRequest();
        publish.setDestination(topic);
        publish.setContentType(PayloadCodec.BASE64_CONTENT_TYPE);
        publish.setPayload(Base64.getEncoder().encodeToString(binary));
        connector.publish(profile, publish);

        List<UnifiedMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        SubscribeRequest subscribe = new SubscribeRequest();
        subscribe.setDestination(topic);
        AutoCloseable subscription = connector.subscribe(profile, subscribe, new MessageHandler() {
            @Override
            public void onMessage(UnifiedMessage message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onError(String error) {}
        });
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        UnifiedMessage message = received.get(0);
        assertThat(message.getContentType()).isEqualTo(PayloadCodec.BASE64_CONTENT_TYPE);
        assertThat(Base64.getDecoder().decode(message.getPayload())).isEqualTo(binary);
    }

    @Test
    void listDestinationsIncludesCreatedTopic() {
        ConnectionProfile profile = profile();
        String topic = "it-list-" + UUID.randomUUID();
        PublishRequest publish = new PublishRequest();
        publish.setDestination(topic);
        publish.setPayload("seed");
        connector.publish(profile, publish);

        List<TopicRef> destinations = connector.listDestinations(profile);
        assertThat(destinations).extracting(TopicRef::getName).contains(topic);
    }
}

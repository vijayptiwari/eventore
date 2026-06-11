package com.eventore.connector.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Broker round-trip tests against a real RabbitMQ started by Testcontainers.
 * Automatically skipped when Docker is not available on the host.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class RabbitMqConnectorIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private final RabbitMqMessagingConnector connector = new RabbitMqMessagingConnector();

    private ConnectionProfile profile() {
        return StreamTestFixtures.profile(
                ProtocolType.RABBITMQ,
                RABBIT.getHost() + ":" + RABBIT.getAmqpPort(),
                Map.of(),
                Map.of("username", RABBIT.getAdminUsername(), "password", RABBIT.getAdminPassword()));
    }

    @Test
    void validateSucceedsAgainstLiveBroker() {
        connector.validate(profile());
    }

    @Test
    void validateAcceptsAmqpSchemeUrl() {
        ConnectionProfile profile = StreamTestFixtures.profile(
                ProtocolType.RABBITMQ,
                "amqp://" + RABBIT.getHost() + ":" + RABBIT.getAmqpPort(),
                Map.of(),
                Map.of("username", RABBIT.getAdminUsername(), "password", RABBIT.getAdminPassword()));
        connector.validate(profile);
    }

    @Test
    void publishSubscribeRoundTripOnQueue() throws Exception {
        ConnectionProfile profile = profile();
        String queue = "it-queue-" + UUID.randomUUID();

        List<UnifiedMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        SubscribeRequest subscribe = new SubscribeRequest();
        subscribe.setDestination(queue);
        AutoCloseable subscription = connector.subscribe(profile, subscribe, new MessageHandler() {
            @Override
            public void onMessage(UnifiedMessage message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onError(String error) {}
        });

        PublishRequest publish = new PublishRequest();
        publish.setDestination(queue);
        publish.setPayload("rabbit-integration");
        connector.publish(profile, publish);

        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        assertThat(received.get(0).getPayload()).isEqualTo("rabbit-integration");
        assertThat(received.get(0).getDestination()).isEqualTo(queue);
    }

    @Test
    void binaryPayloadSurvivesBase64RoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String queue = "it-binary-" + UUID.randomUUID();
        byte[] binary = {(byte) 0xC0, (byte) 0xFF, 0x00, 0x01, (byte) 0xAB};

        List<UnifiedMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        SubscribeRequest subscribe = new SubscribeRequest();
        subscribe.setDestination(queue);
        AutoCloseable subscription = connector.subscribe(profile, subscribe, new MessageHandler() {
            @Override
            public void onMessage(UnifiedMessage message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onError(String error) {}
        });

        PublishRequest publish = new PublishRequest();
        publish.setDestination(queue);
        publish.setContentType(PayloadCodec.BASE64_CONTENT_TYPE);
        publish.setPayload(Base64.getEncoder().encodeToString(binary));
        connector.publish(profile, publish);

        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        UnifiedMessage message = received.get(0);
        assertThat(message.getContentType()).isEqualTo(PayloadCodec.BASE64_CONTENT_TYPE);
        assertThat(Base64.getDecoder().decode(message.getPayload())).isEqualTo(binary);
    }
}

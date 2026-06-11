package com.eventore.connector.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Broker round-trip tests against Eclipse Mosquitto started by Testcontainers.
 * Automatically skipped when Docker is not available on the host.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MqttConnectorIntegrationTest {

    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(
                    DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withExposedPorts(1883)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mosquitto.conf"),
                    "/mosquitto/config/mosquitto.conf");

    private final MqttMessagingConnector connector = new MqttMessagingConnector();

    private ConnectionProfile profile() {
        return StreamTestFixtures.profile(
                ProtocolType.MQTT, "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883));
    }

    @Test
    void validateSucceedsAgainstLiveBroker() {
        connector.validate(profile());
    }

    @Test
    void publishSubscribeTextRoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String topic = "it/mqtt/" + UUID.randomUUID();

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
            PublishRequest publish = new PublishRequest();
            publish.setDestination(topic);
            publish.setPayload("hello-mqtt-integration");
            connector.publish(profile, publish);

            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        assertThat(received.get(0).getPayload()).isEqualTo("hello-mqtt-integration");
        assertThat(received.get(0).getDestination()).isEqualTo(topic);
    }
}

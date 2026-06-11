package com.eventore.connector.jms;

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

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class JmsConnectorIntegrationTest {

    @Container
    static final GenericContainer<?> ARTEMIS = new GenericContainer<>(
                    DockerImageName.parse("apache/activemq-artemis:2.37.0"))
            .withExposedPorts(61616)
            .withEnv("ANONYMOUS_LOGIN", "true");

    private final JmsMessagingConnector connector = new JmsMessagingConnector();

    private ConnectionProfile profile() {
        return StreamTestFixtures.profile(
                ProtocolType.JMS,
                ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
    }

    @Test
    void validateSucceedsAgainstLiveBroker() {
        connector.validate(profile());
    }

    @Test
    void publishSubscribeQueueRoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String queue = "it.jms." + UUID.randomUUID();

        PublishRequest publish = new PublishRequest();
        publish.setDestination(queue);
        publish.setPayload("hello-jms-integration");
        connector.publish(profile, publish);

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
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        assertThat(received.get(0).getPayload()).isEqualTo("hello-jms-integration");
        assertThat(received.get(0).getDestination()).isEqualTo(queue);
    }
}

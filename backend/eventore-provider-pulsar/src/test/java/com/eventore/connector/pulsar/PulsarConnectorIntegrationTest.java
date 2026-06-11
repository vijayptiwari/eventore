package com.eventore.connector.pulsar;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Broker round-trip tests against Pulsar standalone started by Testcontainers.
 * Automatically skipped when Docker is not available on the host.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PulsarConnectorIntegrationTest {

    @Container
    static final GenericContainer<?> PULSAR = new GenericContainer<>(
                    DockerImageName.parse("apachepulsar/pulsar:3.3.2"))
            .withExposedPorts(6650, 8080)
            .withCommand("bin/pulsar", "standalone")
            .waitingFor(Wait.forHttp("/admin/v2/clusters").forPort(8080));

    private final PulsarMessagingConnector connector = new PulsarMessagingConnector();

    @BeforeEach
    void awaitDefaultNamespace() throws Exception {
        String adminUrl = adminUrl();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(adminUrl).build()) {
                if (admin.namespaces().getNamespaces("public").contains("public/default")) {
                    return;
                }
                admin.namespaces().createNamespace("public/default");
                return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Pulsar public/default namespace not ready");
    }

    private static String adminUrl() {
        return "http://" + PULSAR.getHost() + ":" + PULSAR.getMappedPort(8080);
    }

    private ConnectionProfile profile() {
        String broker = "pulsar://" + PULSAR.getHost() + ":" + PULSAR.getMappedPort(6650);
        return StreamTestFixtures.profile(
                ProtocolType.PULSAR, broker, Map.of("adminUrl", adminUrl()), Map.of());
    }

    @Test
    void validateSucceedsAgainstLiveBroker() {
        connector.validate(profile());
    }

    @Test
    void publishSubscribeTextRoundTrip() throws Exception {
        ConnectionProfile profile = profile();
        String topic = "it-pulsar-" + UUID.randomUUID();

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
            publish.setPayload("hello-pulsar-integration");
            connector.publish(profile, publish);

            assertThat(latch.await(45, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.close();
        }
        assertThat(received.get(0).getPayload()).isEqualTo("hello-pulsar-integration");
    }
}

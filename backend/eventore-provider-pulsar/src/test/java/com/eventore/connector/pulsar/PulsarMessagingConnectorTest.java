package com.eventore.connector.pulsar;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.admin.Namespaces;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulsarMessagingConnectorTest {

    private PulsarMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new PulsarMessagingConnector();
    }

    @Test
    void protocolIsPulsar() {
        assertEquals(ProtocolType.PULSAR, connector.protocol());
    }

    @Test
    void closeWithNoActiveClientsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-pulsar"));
    }

    @Test
    void subscribeWithInvalidServiceUrlIsWrappedAsIllegalState() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "not-a-valid-url");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("Pulsar subscribe failed"));
    }

    @Test
    void subscribeSetupFailureClosesBuiltClient() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");
        PulsarClient client = mock(PulsarClient.class);
        ConsumerBuilder<byte[]> consumerBuilder = mock(ConsumerBuilder.class);
        when(client.newConsumer(any())).thenAnswer(inv -> consumerBuilder);
        when(consumerBuilder.topics(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionName(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionType(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.messageListener(any())).thenReturn(consumerBuilder);
        doThrow(new RuntimeException("subscribe failed")).when(consumerBuilder).subscribe();

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));
            verify(client).close();
        }
    }

    @Test
    void publishWithInvalidServiceUrlIsWrappedAsIllegalState() {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "not-a-valid-url");
        PublishRequest request = new PublishRequest();
        request.setDestination("my-topic");
        request.setPayload("hello");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertTrue(ex.getMessage().contains("Pulsar publish failed"));
    }

    @Test
    void publishRejectsInvalidBase64Payload() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        PublishRequest request = new PublishRequest();
        request.setDestination("my-topic");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");
        PulsarClient client = mock(PulsarClient.class);
        ProducerBuilder<byte[]> producerBuilder = mock(ProducerBuilder.class);
        Producer<byte[]> producer = mock(Producer.class);
        when(client.newProducer(any())).thenAnswer(inv -> producerBuilder);
        when(producerBuilder.topic(anyString())).thenReturn(producerBuilder);
        doReturn(producer).when(producerBuilder).create();
        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getMessage().contains("Pulsar publish failed"));
            verify(client).close();
        }
    }

    @Test
    void closeRemovesRegisteredSubscriptionClient() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");
        PulsarClient client = mock(PulsarClient.class);
        Consumer<byte[]> consumer = mock(Consumer.class);
        ConsumerBuilder<byte[]> consumerBuilder = mock(ConsumerBuilder.class);
        when(client.newConsumer(any())).thenAnswer(inv -> consumerBuilder);
        when(consumerBuilder.topics(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionName(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionType(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.messageListener(any())).thenReturn(consumerBuilder);
        doReturn(consumer).when(consumerBuilder).subscribe();

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            subscription.close();
            verify(consumer).close();
            verify(client).close();
            assertDoesNotThrow(() -> connector.close(profile.getId()));
        }
    }

    @Test
    void validateFailsWhenPartitionProbeAndAdminBothFail() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        PulsarClient client = mock(PulsarClient.class);
        when(client.getPartitionsForTopic(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("topic missing")));

        PulsarAdmin admin = mock(PulsarAdmin.class);
        Namespaces namespaces = mock(Namespaces.class);
        when(admin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces(anyString())).thenThrow(new RuntimeException("admin unreachable"));

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        var adminBuilder = mock(org.apache.pulsar.client.admin.PulsarAdminBuilder.class);
        when(adminBuilder.serviceHttpUrl(anyString())).thenReturn(adminBuilder);
        doReturn(admin).when(adminBuilder).build();

        try (MockedStatic<PulsarClient> pulsarClient = mockStatic(PulsarClient.class);
                MockedStatic<PulsarAdmin> pulsarAdmin = mockStatic(PulsarAdmin.class)) {
            pulsarClient.when(PulsarClient::builder).thenReturn(clientBuilder);
            pulsarAdmin.when(PulsarAdmin::builder).thenReturn(adminBuilder);

            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> connector.validate(profile));

            assertTrue(ex.getMessage().contains("Pulsar connection failed"));
            verify(client).close();
            verify(admin).close();
        }
    }

    @Test
    void validateSucceedsWhenPartitionProbeFailsButAdminWorks() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        PulsarClient client = mock(PulsarClient.class);
        when(client.getPartitionsForTopic(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("topic missing")));

        PulsarAdmin admin = mock(PulsarAdmin.class);
        Namespaces namespaces = mock(Namespaces.class);
        when(admin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces("public")).thenReturn(List.of("public/default"));

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        var adminBuilder = mock(org.apache.pulsar.client.admin.PulsarAdminBuilder.class);
        when(adminBuilder.serviceHttpUrl(anyString())).thenReturn(adminBuilder);
        doReturn(admin).when(adminBuilder).build();

        try (MockedStatic<PulsarClient> pulsarClient = mockStatic(PulsarClient.class);
                MockedStatic<PulsarAdmin> pulsarAdmin = mockStatic(PulsarAdmin.class)) {
            pulsarClient.when(PulsarClient::builder).thenReturn(clientBuilder);
            pulsarAdmin.when(PulsarAdmin::builder).thenReturn(adminBuilder);

            assertDoesNotThrow(() -> connector.validate(profile));
            verify(client).close();
            verify(admin).close();
        }
    }

    @Test
    void subscribeFailsWhenNoDestinationProvidedAndClosesClient() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        SubscribeRequest request = new SubscribeRequest();
        PulsarClient client = mock(PulsarClient.class);
        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("destination"));
            verify(client).close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeUsesConsumerGroupAsSubscriptionName() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");
        request.setConsumerGroup("orders-group");
        PulsarClient client = mock(PulsarClient.class);
        Consumer<byte[]> consumer = mock(Consumer.class);
        ConsumerBuilder<byte[]> consumerBuilder = mock(ConsumerBuilder.class);
        when(client.newConsumer(any())).thenAnswer(inv -> consumerBuilder);
        when(consumerBuilder.topics(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionName(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionType(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.messageListener(any())).thenReturn(consumerBuilder);
        doReturn(consumer).when(consumerBuilder).subscribe();

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            ArgumentCaptor<String> subscriptionCaptor = ArgumentCaptor.forClass(String.class);
            verify(consumerBuilder).subscriptionName(subscriptionCaptor.capture());
            assertEquals("orders-group", subscriptionCaptor.getValue());
            subscription.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeWithExplicitSubscriptionKeyReplacesPreviousClient() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.PULSAR, "pulsar://localhost:6650");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");
        request.setSubscriptionKey("sub-key-1");

        PulsarClient client1 = mock(PulsarClient.class);
        PulsarClient client2 = mock(PulsarClient.class);
        Consumer<byte[]> consumer1 = mock(Consumer.class);
        Consumer<byte[]> consumer2 = mock(Consumer.class);
        ConsumerBuilder<byte[]> consumerBuilder = mock(ConsumerBuilder.class);
        when(client1.newConsumer(any())).thenAnswer(inv -> consumerBuilder);
        when(client2.newConsumer(any())).thenAnswer(inv -> consumerBuilder);
        when(consumerBuilder.topics(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionName(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionType(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.messageListener(any())).thenReturn(consumerBuilder);
        doReturn(consumer1, consumer2).when(consumerBuilder).subscribe();

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.serviceUrl(anyString())).thenReturn(clientBuilder);
        doReturn(client1, client2).when(clientBuilder).build();

        try (MockedStatic<PulsarClient> pulsar = mockStatic(PulsarClient.class)) {
            pulsar.when(PulsarClient::builder).thenReturn(clientBuilder);

            AutoCloseable sub1 = connector.subscribe(profile, request, msg -> {});
            AutoCloseable sub2 = connector.subscribe(profile, request, msg -> {});

            verify(client1).close();
            sub1.close();
            sub2.close();
        }
    }
}

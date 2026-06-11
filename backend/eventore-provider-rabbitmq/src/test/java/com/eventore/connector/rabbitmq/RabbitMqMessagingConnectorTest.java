package com.eventore.connector.rabbitmq;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitMqMessagingConnectorTest {

    private RabbitMqMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new RabbitMqMessagingConnector();
    }

    @Test
    void protocolIsRabbitMq() {
        assertEquals(ProtocolType.RABBITMQ, connector.protocol());
    }

    @Test
    void closeWithNoActiveConnectionsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-rabbit"));
    }

    @Test
    void subscribeFailureIsWrappedAsIllegalState() {
        // Port 1 is reserved; the connection is refused locally without any broker.
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "127.0.0.1:1");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("RabbitMQ subscribe failed"));
    }

    @Test
    void subscribeSetupFailureClosesOpenedConnection() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");
        Connection conn = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(conn.createChannel()).thenReturn(channel);
        doThrow(new IOException("queue declare failed"))
                .when(channel)
                .queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), isNull());

        try (MockedConstruction<ConnectionFactory> factories = mockConstruction(
                ConnectionFactory.class,
                (factory, ctx) -> when(factory.newConnection()).thenReturn(conn))) {
            assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));
            verify(conn).close();
        }
    }

    @Test
    void closeRemovesRegisteredSubscriptionConnection() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");
        Connection conn = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(conn.createChannel()).thenReturn(channel);
        when(channel.basicConsume(
                anyString(), anyBoolean(), any(DeliverCallback.class), any(CancelCallback.class)))
                .thenReturn("consumer-tag");

        try (MockedConstruction<ConnectionFactory> factories = mockConstruction(
                ConnectionFactory.class,
                (factory, ctx) -> when(factory.newConnection()).thenReturn(conn))) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            subscription.close();
            verify(channel).basicCancel("consumer-tag");
            verify(channel).close();
            verify(conn).close();
            assertDoesNotThrow(() -> connector.close(profile.getId()));
        }
    }

    @Test
    void publishFailureIsWrappedAsIllegalState() {
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "127.0.0.1:1");
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("hello");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertTrue(ex.getMessage().contains("RabbitMQ publish failed"));
    }

    @Test
    void publishRejectsInvalidBase64Payload() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672");
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");
        Connection conn = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(conn.createChannel()).thenReturn(channel);

        try (MockedConstruction<ConnectionFactory> factories = mockConstruction(
                ConnectionFactory.class,
                (factory, ctx) -> when(factory.newConnection()).thenReturn(conn))) {
            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getMessage().contains("RabbitMQ publish failed"));
        }
    }
}

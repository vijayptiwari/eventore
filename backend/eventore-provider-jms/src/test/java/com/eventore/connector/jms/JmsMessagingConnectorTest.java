package com.eventore.connector.jms;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.testsupport.StreamTestFixtures;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JmsMessagingConnectorTest {

    private JmsMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new JmsMessagingConnector();
    }

    @Test
    void protocolIsJms() {
        assertEquals(ProtocolType.JMS, connector.protocol());
    }

    @Test
    void listDestinationsReturnsQueueAndOptionalTopic() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.JMS, "localhost:61616", Map.of("queue", "orders.in", "topic", "events.out"), null);

        List<TopicRef> destinations = connector.listDestinations(profile);

        assertEquals(2, destinations.size());
        assertEquals("orders.in", destinations.get(0).getName());
        assertEquals("queue", destinations.get(0).getType());
        assertEquals("events.out", destinations.get(1).getName());
        assertEquals("topic", destinations.get(1).getType());
    }

    @Test
    void listDestinationsDefaultsQueueName() {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616", null, null);

        assertEquals("eventore.queue", connector.listDestinations(profile).get(0).getName());
    }

    @Test
    void closeWithNoActiveConnectionsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-jms"));
    }

    @Test
    void subscribeFailureIsWrappedAsIllegalStateAndDoesNotLeakConnection() {
        // Port 1 is reserved; the connection is refused locally without any broker.
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "127.0.0.1:1");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders.in");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("JMS subscribe failed"));
        assertDoesNotThrow(() -> connector.close(profile.getId()));
    }

    @Test
    void subscribeSetupFailureClosesOpenedConnection() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders.in");
        Connection conn = mock(Connection.class);
        doThrow(new JMSException("session failed"))
                .when(conn)
                .createSession(anyBoolean(), anyInt());

        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (factory, ctx) -> when(factory.createConnection()).thenReturn(conn))) {
            assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));
            verify(conn).close();
        }
    }

    @Test
    void closeRemovesRegisteredSubscriptionConnection() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders.in");
        Connection conn = mock(Connection.class);
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(conn.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(queue);
        when(session.createConsumer(queue)).thenReturn(consumer);

        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (factory, ctx) -> when(factory.createConnection()).thenReturn(conn))) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            subscription.close();
            verify(consumer).close();
            verify(session).close();
            verify(conn).close();
            assertDoesNotThrow(() -> connector.close(profile.getId()));
        }
    }

    @Test
    void publishRejectsInvalidBase64Payload() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616");
        PublishRequest request = new PublishRequest();
        request.setDestination("orders.in");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");
        Connection conn = mock(Connection.class);
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        BytesMessage bytesMessage = mock(BytesMessage.class);
        when(conn.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(queue);
        when(session.createBytesMessage()).thenReturn(bytesMessage);

        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (factory, ctx) -> when(factory.createConnection()).thenReturn(conn))) {
            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getMessage().contains("JMS publish failed"));
        }
    }

    @Test
    void subscribeWithNullOptionsUsesDefaultDestinationTypeWithoutNpe() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders.in");
        setFieldNull(request, "options");

        Connection conn = mock(Connection.class);
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(conn.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(queue);
        when(session.createConsumer(queue)).thenReturn(consumer);

        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (factory, ctx) -> when(factory.createConnection()).thenReturn(conn))) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            verify(session).createQueue("orders.in");
            subscription.close();
        }
    }

    @Test
    void publishWithNullHeadersUsesDefaultDestinationTypeWithoutNpe() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.JMS, "localhost:61616");
        PublishRequest request = new PublishRequest();
        request.setDestination("orders.in");
        request.setPayload("hello");
        setFieldNull(request, "headers");

        Connection conn = mock(Connection.class);
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        TextMessage textMessage = mock(TextMessage.class);
        javax.jms.MessageProducer producer = mock(javax.jms.MessageProducer.class);
        when(conn.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(queue);
        when(session.createTextMessage("hello")).thenReturn(textMessage);
        when(session.createProducer(queue)).thenReturn(producer);

        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (factory, ctx) -> when(factory.createConnection()).thenReturn(conn))) {
            assertDoesNotThrow(() -> connector.publish(profile, request));
            verify(session).createQueue("orders.in");
            verify(producer).send(textMessage);
        }
    }

    private static void setFieldNull(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, null);
    }
}

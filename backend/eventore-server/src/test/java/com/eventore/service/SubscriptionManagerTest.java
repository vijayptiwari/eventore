package com.eventore.service;

import com.eventore.config.EventoreProperties;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.service.SubscriptionManager.StreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionManagerTest {

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private MetricsService metricsService;

    @Mock
    private MessagingConnector connector;

    private EventoreProperties props;
    private SubscriptionManager manager;

    @BeforeEach
    void setUp() {
        props = new EventoreProperties();
        manager = new SubscriptionManager(connectorRegistry, props, metricsService);
    }

    private static ConnectionProfile kafkaProfile(String connectionId) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId(connectionId);
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("localhost:9092");
        return profile;
    }

    private void stubConnector() {
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        when(connector.subscribe(any(), any(), any())).thenReturn(() -> {});
    }

    @Test
    void ownsSubscriptionChecksConnectionId() {
        stubConnector();
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        String subId = manager.subscribe(kafkaProfile("conn-a"), request, event -> {}, true);

        assertTrue(manager.ownsSubscription("conn-a", subId));
        assertFalse(manager.ownsSubscription("conn-b", subId));
    }

    @Test
    void queueRequiresExistingSubscription() {
        assertThrows(ResponseStatusException.class, () -> manager.queue("missing-sub"));
    }

    @Test
    void unsubscribeClosesConnectorSubscriptionAndReleasesSlot() {
        AtomicBoolean closed = new AtomicBoolean(false);
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        when(connector.subscribe(any(), any(), any())).thenReturn(() -> closed.set(true));

        String subId = manager.subscribe(kafkaProfile("conn-a"), new SubscribeRequest(), e -> {}, false);
        assertEquals(1, manager.activeCount());

        manager.unsubscribe(subId);

        assertTrue(closed.get());
        assertEquals(0, manager.activeCount());
        verify(metricsService).decrementSubscriptions();
        assertFalse(manager.ownsSubscription("conn-a", subId));
    }

    @Test
    void queueOverflowDropsOldestAndEmitsSlowConsumer() {
        props.getSubscriptions().setQueueCapacity(1);
        stubConnector();

        List<StreamEvent> delivered = new ArrayList<>();
        String subId = manager.subscribe(kafkaProfile("conn-a"), new SubscribeRequest(), delivered::add, true);

        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        verify(connector).subscribe(any(), any(), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        UnifiedMessage first = new UnifiedMessage();
        first.setPayload("first");
        UnifiedMessage second = new UnifiedMessage();
        second.setPayload("second");
        handler.onMessage(first);
        handler.onMessage(second);

        assertEquals(1, manager.queue(subId).size());
        assertEquals("second", manager.queue(subId).peek().message().getPayload());
        assertTrue(delivered.stream().anyMatch(e -> "SLOW_CONSUMER".equals(e.type())));
    }

    @Test
    void closeAllForConnectionOnlyClosesThatConnectionsSubscriptions() {
        AtomicBoolean closedA = new AtomicBoolean(false);
        AtomicBoolean closedB = new AtomicBoolean(false);
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        when(connector.subscribe(any(), any(), any()))
                .thenReturn(() -> closedA.set(true))
                .thenReturn(() -> closedB.set(true));

        String subA = manager.subscribe(kafkaProfile("conn-a"), new SubscribeRequest(), e -> {}, false);
        String subB = manager.subscribe(kafkaProfile("conn-b"), new SubscribeRequest(), e -> {}, false);

        manager.closeAllForConnection("conn-a");

        assertTrue(closedA.get());
        assertFalse(closedB.get());
        assertFalse(manager.ownsSubscription("conn-a", subA));
        assertTrue(manager.ownsSubscription("conn-b", subB));
        assertEquals(1, manager.activeCount());
    }

    @Test
    void rejectsSubscriptionsBeyondMaxConcurrent() {
        props.getSubscriptions().setMaxConcurrent(1);
        stubConnector();

        manager.subscribe(kafkaProfile("conn-a"), new SubscribeRequest(), e -> {}, false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> manager.subscribe(kafkaProfile("conn-a"), new SubscribeRequest(), e -> {}, false));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }
}

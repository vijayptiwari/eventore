package com.eventore.config;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.service.MetricsService;
import com.eventore.service.SubscriptionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoreHealthIndicatorTest {

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private MessagingConnector connector;

    private EventoreProperties properties;
    private SubscriptionManager subscriptionManager;
    private EventoreHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        properties = new EventoreProperties();
        properties.getDiagnostics().setErrorSubscriptionThreshold(2);
        MetricsService metricsService = new MetricsService(new SimpleMeterRegistry());
        subscriptionManager = new SubscriptionManager(connectorRegistry, properties, metricsService);
        indicator = new EventoreHealthIndicator(properties, subscriptionManager);
    }

    @Test
    void healthDownWhenErrorSubscriptionsExceedThreshold() {
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        when(connector.subscribe(any(), any(), any())).thenAnswer(invocation -> {
            MessageHandler handler = invocation.getArgument(2);
            handler.onError("broker down");
            return (AutoCloseable) () -> {};
        });
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("c1");
        profile.setName("kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("localhost:9092");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        subscriptionManager.subscribe(profile, request, event -> {}, false);
        subscriptionManager.subscribe(profile, request, event -> {}, false);

        assertEquals(Status.DOWN, indicator.health().getStatus());
        assertEquals(2, indicator.health().getDetails().get("subscriptionsInError"));
    }

    @Test
    void healthUpBelowThreshold() {
        properties.getDiagnostics().setErrorSubscriptionThreshold(5);
        assertEquals(Status.UP, indicator.health().getStatus());
    }
}

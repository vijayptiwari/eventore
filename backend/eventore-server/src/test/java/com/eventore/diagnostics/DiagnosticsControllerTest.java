package com.eventore.diagnostics;

import com.eventore.api.delegate.CoreConnectionsApiDelegateImpl;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.MetricsService;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.ValidationHistoryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticsControllerTest {

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private MessagingConnector connector;

    private ConnectionRegistry connectionRegistry;
    private MetricsService metricsService;
    private SubscriptionManager subscriptionManager;
    private ValidationHistoryService validationHistoryService;
    private DiagnosticsController diagnosticsController;
    private CoreConnectionsApiDelegateImpl connectionsDelegate;

    @BeforeEach
    void setUp() {
        connectionRegistry = new ConnectionRegistry();
        metricsService = new MetricsService(new SimpleMeterRegistry());
        subscriptionManager = new SubscriptionManager(connectorRegistry, new com.eventore.config.EventoreProperties(), metricsService);
        validationHistoryService = new ValidationHistoryService(metricsService);
        diagnosticsController =
                new DiagnosticsController(subscriptionManager, validationHistoryService, connectionRegistry, policy);
        connectionsDelegate = new CoreConnectionsApiDelegateImpl(
                connectionRegistry, connectorRegistry, policy, subscriptionManager, validationHistoryService);
        doNothing().when(policy).require(any());
    }

    @Test
    void subscriptionsEndpointListsActiveSubscriptions() {
        ConnectionProfile profile = profile("conn-1", "Kafka Dev");
        connectionRegistry.save(profile);
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        when(connector.subscribe(any(), any(), any())).thenReturn(() -> {});

        var subscribeRequest = new com.eventore.connector.spi.SubscribeRequest();
        subscribeRequest.setDestination("orders");
        subscriptionManager.subscribe(profile, subscribeRequest, event -> {}, true);

        List<SubscriptionDiagnosticDto> rows = diagnosticsController.subscriptions();

        assertEquals(1, rows.size());
        assertEquals("conn-1", rows.get(0).connectionId());
        assertEquals("Kafka Dev", rows.get(0).connectionName());
        assertEquals("SSE", rows.get(0).transport());
        assertEquals("ACTIVE", rows.get(0).status());
    }

    @Test
    void validationHistoryAppendedOnValidate() {
        ConnectionProfile profile = profile("conn-2", "Kafka QA");
        connectionRegistry.save(profile);
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);
        doNothing().when(connector).validate(any());

        connectionsDelegate.validateConnection("conn-2");

        List<ValidationRecordDto> history = diagnosticsController.validationHistory("conn-2");
        assertEquals(1, history.size());
        assertEquals("OK", history.get(0).status());
    }

    @Test
    void emptySubscriptionsReturnsEmptyList() {
        assertTrue(diagnosticsController.subscriptions().isEmpty());
    }

    private static ConnectionProfile profile(String id, String name) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setProtocol(ProtocolType.KAFKA);
        profile.setBrokerUrl("localhost:9092");
        return profile;
    }
}

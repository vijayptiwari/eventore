package com.eventore.api.delegate;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.MetricsService;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.ValidationHistoryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreConnectionsApiDelegateImplTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private AuditService auditService;

    private CoreConnectionsApiDelegateImpl delegate;

    @BeforeEach
    void setUp() {
        ValidationHistoryService validationHistoryService =
                new ValidationHistoryService(new MetricsService(new SimpleMeterRegistry()));
        delegate = new CoreConnectionsApiDelegateImpl(
                connectionRegistry,
                connectorRegistry,
                policy,
                subscriptionManager,
                validationHistoryService,
                auditService);
    }

    @Test
    void updateConnectionReturns404WhenMissing() {
        when(connectionRegistry.find("missing-conn")).thenReturn(Optional.empty());

        ConnectionProfile update = new ConnectionProfile();
        update.setProtocol(ProtocolType.KAFKA);

        doNothing().when(policy).require(any());
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> delegate.updateConnection("missing-conn", update));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(connectionRegistry, never()).save(any());
    }
}

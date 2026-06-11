package com.eventore.api.delegate;

import com.eventore.api.dto.ConnectionProfileResponse;
import com.eventore.api.generated.core.ConnectionsApiDelegate;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.domain.ConnectionProfile;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.ValidationHistoryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoreConnectionsApiDelegateImpl implements ConnectionsApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final DeploymentModePolicy policy;
    private final SubscriptionManager subscriptionManager;
    private final ValidationHistoryService validationHistoryService;
    private final AuditService auditService;

    public CoreConnectionsApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            DeploymentModePolicy policy,
            SubscriptionManager subscriptionManager,
            ValidationHistoryService validationHistoryService,
            AuditService auditService) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.policy = policy;
        this.subscriptionManager = subscriptionManager;
        this.validationHistoryService = validationHistoryService;
        this.auditService = auditService;
    }

    @Override
    public ResponseEntity<List<ConnectionProfileResponse>> listConnections() {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(connectionRegistry.list().stream()
                .map(ConnectionProfileResponse::from)
                .collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<ConnectionProfileResponse> getConnection(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(ConnectionProfileResponse.from(CoreDelegateSupport.profile(connectionRegistry, connectionId)));
    }

    @Override
    public ResponseEntity<ConnectionProfileResponse> createConnection(ConnectionProfile connectionProfile) {
        policy.require(Action.MANAGE_CONNECTIONS);
        policy.requireProtocol(connectionProfile.getProtocol());
        ConnectionProfile saved = connectionRegistry.save(connectionProfile);
        auditService.connectionCreated(saved.getId(), saved.getProtocol(), saved.getName());
        return ResponseEntity.ok(ConnectionProfileResponse.from(saved));
    }

    @Override
    public ResponseEntity<ConnectionProfileResponse> updateConnection(
            String connectionId, ConnectionProfile connectionProfile) {
        policy.require(Action.MANAGE_CONNECTIONS);
        policy.requireProtocol(connectionProfile.getProtocol());
        CoreDelegateSupport.profile(connectionRegistry, connectionId);
        connectionProfile.setId(connectionId);
        ConnectionProfile saved = connectionRegistry.save(connectionProfile);
        auditService.connectionUpdated(saved.getId(), saved.getProtocol(), saved.getName());
        return ResponseEntity.ok(ConnectionProfileResponse.from(saved));
    }

    @Override
    public ResponseEntity<Void> deleteConnection(String connectionId) {
        policy.require(Action.MANAGE_CONNECTIONS);
        ConnectionProfile profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        subscriptionManager.closeAllForConnection(connectionId);
        connectorRegistry.get(profile.getProtocol()).close(connectionId);
        connectionRegistry.delete(connectionId);
        auditService.connectionDeleted(connectionId, profile.getProtocol());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Map<String, String>> validateConnection(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        policy.requireProtocol(profile.getProtocol());
        try {
            connectorRegistry.get(profile.getProtocol()).validate(profile);
            validationHistoryService.recordSuccess(connectionId, profile.getProtocol());
            auditService.validateConnection(connectionId, profile.getProtocol(), true, "ok");
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (RuntimeException e) {
            validationHistoryService.recordFailure(connectionId, profile.getProtocol(), e.getMessage());
            auditService.validateConnection(connectionId, profile.getProtocol(), false, e.getMessage());
            throw e;
        }
    }
}

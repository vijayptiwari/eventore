package com.eventore.api.delegate;

import com.eventore.api.generated.core.PublishApiDelegate;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CorePublishApiDelegateImpl implements PublishApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final DeploymentModePolicy policy;
    private final AuditService auditService;

    public CorePublishApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.policy = policy;
        this.auditService = auditService;
    }

    @Override
    public ResponseEntity<Map<String, String>> publishMessage(String connectionId, PublishRequest publishRequest) {
        policy.require(Action.PUBLISH);
        var profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        policy.requireProtocol(profile.getProtocol());
        int bytes = publishRequest.getPayload() != null
                ? publishRequest.getPayload().getBytes(StandardCharsets.UTF_8).length
                : 0;
        policy.validatePublishSize(bytes);
        connectorRegistry.get(profile.getProtocol()).publish(profile, publishRequest);
        var request = CoreDelegateSupport.currentRequest();
        auditService.publish(
                connectionId,
                profile.getProtocol(),
                publishRequest.getDestination(),
                bytes,
                request != null ? request.getHeader("User-Agent") : null);
        return ResponseEntity.ok(Map.of("status", "published"));
    }
}

package com.eventore.api.delegate;

import com.eventore.api.generated.core.DestinationsApiDelegate;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.domain.TopicRef;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoreDestinationsApiDelegateImpl implements DestinationsApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final DeploymentModePolicy policy;

    public CoreDestinationsApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            DeploymentModePolicy policy) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.policy = policy;
    }

    @Override
    public ResponseEntity<List<TopicRef>> listDestinations(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        var profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        policy.requireProtocol(profile.getProtocol());
        return ResponseEntity.ok(connectorRegistry.get(profile.getProtocol()).listDestinations(profile));
    }
}

package com.eventore.api.delegate;

import com.eventore.api.generated.core.SubscribeApiDelegate;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.SubscriptionManager;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CoreSubscribeApiDelegateImpl implements SubscribeApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final SubscriptionManager subscriptionManager;
    private final DeploymentModePolicy policy;

    public CoreSubscribeApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            SubscriptionManager subscriptionManager,
            DeploymentModePolicy policy) {
        this.connectionRegistry = connectionRegistry;
        this.subscriptionManager = subscriptionManager;
        this.policy = policy;
    }

    @Override
    public ResponseEntity<Map<String, String>> startSubscription(
            String connectionId, SubscribeRequest subscribeRequest) {
        policy.require(Action.SUBSCRIBE);
        var profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        policy.requireProtocol(profile.getProtocol());
        String subscriptionId =
                subscriptionManager.subscribe(profile, subscribeRequest, event -> {}, true);
        return ResponseEntity.ok(Map.of(
                "subscriptionId", subscriptionId,
                "sseUrl", "/api/v1/stream/" + subscriptionId + "?connectionId=" + connectionId));
    }

    @Override
    public ResponseEntity<Map<String, String>> stopSubscription(String connectionId, String subscriptionId) {
        policy.require(Action.SUBSCRIBE);
        if (!subscriptionManager.ownsSubscription(connectionId, subscriptionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Subscription not owned by connection");
        }
        subscriptionManager.unsubscribe(subscriptionId);
        return ResponseEntity.ok(Map.of("status", "unsubscribed"));
    }
}

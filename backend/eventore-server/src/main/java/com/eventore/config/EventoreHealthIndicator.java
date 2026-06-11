package com.eventore.config;

import com.eventore.service.SubscriptionManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class EventoreHealthIndicator implements HealthIndicator {

    private final EventoreProperties properties;
    private final SubscriptionManager subscriptionManager;

    public EventoreHealthIndicator(
            EventoreProperties properties, SubscriptionManager subscriptionManager) {
        this.properties = properties;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public Health health() {
        int subscriptionsInError = subscriptionManager.countSubscriptionsInError();
        int threshold = properties.getDiagnostics().getErrorSubscriptionThreshold();
        Health.Builder builder = subscriptionsInError >= threshold ? Health.down() : Health.up();
        return builder.withDetail("deploymentMode", properties.getDeploymentMode())
                .withDetail("activeSubscriptions", subscriptionManager.activeCount())
                .withDetail("subscriptionsInError", subscriptionsInError)
                .withDetail("threshold", threshold)
                .build();
    }
}

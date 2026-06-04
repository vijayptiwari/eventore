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
        return Health.up()
                .withDetail("deploymentMode", properties.getDeploymentMode())
                .withDetail("activeSubscriptions", subscriptionManager.activeCount())
                .build();
    }
}

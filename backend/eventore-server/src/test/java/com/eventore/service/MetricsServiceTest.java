package com.eventore.service;

import com.eventore.domain.ProtocolType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsServiceTest {

    @Test
    void recordsSubscriptionErrorsAndValidationCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsService metrics = new MetricsService(registry);

        metrics.recordSubscriptionError();
        metrics.recordSubscriptionError();
        metrics.recordValidation(ProtocolType.KAFKA, true);
        metrics.recordValidation(ProtocolType.KAFKA, false);
        metrics.refreshSubscriptionErrorGauge(2);

        assertEquals(
                2.0,
                registry.get("eventore.subscription.errors.total").counter().count());
        assertEquals(
                1.0,
                registry.get("eventore.connection.validations.total")
                        .tag("result", "success")
                        .tag("protocol", "KAFKA")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.get("eventore.connection.validations.total")
                        .tag("result", "failure")
                        .tag("protocol", "KAFKA")
                        .counter()
                        .count());
        assertEquals(2.0, registry.get("eventore.subscriptions.errors").gauge().value());
    }
}

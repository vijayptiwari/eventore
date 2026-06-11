package com.eventore.service;

import com.eventore.domain.ProtocolType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Map<ProtocolType, Counter> messageCounters = new EnumMap<>(ProtocolType.class);
    private final Map<ProtocolType, Counter> validationSuccessCounters = new EnumMap<>(ProtocolType.class);
    private final Map<ProtocolType, Counter> validationFailureCounters = new EnumMap<>(ProtocolType.class);
    private final AtomicInteger activeSubscriptions = new AtomicInteger();
    private final AtomicInteger wsConnections = new AtomicInteger();
    private final AtomicInteger subscriptionsInError = new AtomicInteger();
    private final Counter subscriptionErrorsTotal;

    public MetricsService(MeterRegistry registry) {
        for (ProtocolType type : ProtocolType.values()) {
            messageCounters.put(
                    type,
                    Counter.builder("eventore.messages.received")
                            .tag("protocol", type.name())
                            .register(registry));
            validationSuccessCounters.put(
                    type,
                    Counter.builder("eventore.connection.validations.total")
                            .tag("result", "success")
                            .tag("protocol", type.name())
                            .register(registry));
            validationFailureCounters.put(
                    type,
                    Counter.builder("eventore.connection.validations.total")
                            .tag("result", "failure")
                            .tag("protocol", type.name())
                            .register(registry));
        }
        subscriptionErrorsTotal = Counter.builder("eventore.subscription.errors.total")
                .register(registry);
        Gauge.builder("eventore.subscriptions.active", activeSubscriptions, AtomicInteger::get)
                .register(registry);
        Gauge.builder("eventore.subscriptions.errors", subscriptionsInError, AtomicInteger::get)
                .register(registry);
        Gauge.builder("eventore.websocket.connections", wsConnections, AtomicInteger::get)
                .register(registry);
    }

    public void recordMessage(ProtocolType protocol) {
        Counter counter = messageCounters.get(protocol);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordValidation(ProtocolType protocol, boolean success) {
        Counter counter = success ? validationSuccessCounters.get(protocol) : validationFailureCounters.get(protocol);
        if (counter != null) {
            counter.increment();
        }
    }

    public void recordSubscriptionError() {
        subscriptionErrorsTotal.increment();
    }

    public void refreshSubscriptionErrorGauge(int count) {
        subscriptionsInError.set(count);
    }

    public int incrementSubscriptions() {
        return activeSubscriptions.incrementAndGet();
    }

    public int decrementSubscriptions() {
        return activeSubscriptions.decrementAndGet();
    }

    public int incrementWs() {
        return wsConnections.incrementAndGet();
    }

    public int decrementWs() {
        return wsConnections.decrementAndGet();
    }
}

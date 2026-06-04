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
    private final AtomicInteger activeSubscriptions = new AtomicInteger();
    private final AtomicInteger wsConnections = new AtomicInteger();

    public MetricsService(MeterRegistry registry) {
        for (ProtocolType type : ProtocolType.values()) {
            messageCounters.put(
                    type,
                    Counter.builder("eventore.messages.received")
                            .tag("protocol", type.name())
                            .register(registry));
        }
        Gauge.builder("eventore.subscriptions.active", activeSubscriptions, AtomicInteger::get)
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

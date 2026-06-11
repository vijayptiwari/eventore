package com.eventore.service;

import com.eventore.domain.ProtocolType;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ValidationHistoryService {

    private static final int MAX_ENTRIES = 10;

    private final Map<String, Deque<ValidationRecord>> history = new ConcurrentHashMap<>();
    private final MetricsService metricsService;

    public ValidationHistoryService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void recordSuccess(String connectionId, ProtocolType protocol) {
        append(connectionId, new ValidationRecord(Instant.now(), "OK", "Connection validated"));
        metricsService.recordValidation(protocol, true);
    }

    public void recordFailure(String connectionId, ProtocolType protocol, String message) {
        String safe = message != null ? message : "Validation failed";
        append(connectionId, new ValidationRecord(Instant.now(), "FAILED", safe));
        metricsService.recordValidation(protocol, false);
    }

    public List<ValidationRecord> history(String connectionId) {
        Deque<ValidationRecord> deque = history.get(connectionId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return List.copyOf(deque);
    }

    private void append(String connectionId, ValidationRecord record) {
        Deque<ValidationRecord> deque =
                history.computeIfAbsent(connectionId, id -> new ArrayDeque<>(MAX_ENTRIES));
        synchronized (deque) {
            if (deque.size() >= MAX_ENTRIES) {
                deque.removeFirst();
            }
            deque.addLast(record);
        }
    }

    public record ValidationRecord(Instant timestamp, String status, String message) {}
}

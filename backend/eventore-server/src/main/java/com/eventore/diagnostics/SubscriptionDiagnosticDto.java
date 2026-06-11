package com.eventore.diagnostics;

public record SubscriptionDiagnosticDto(
        String subscriptionId,
        String connectionId,
        String connectionName,
        String protocol,
        String destination,
        String transport,
        int messageCount,
        String lastError,
        String startedAt,
        String status) {}

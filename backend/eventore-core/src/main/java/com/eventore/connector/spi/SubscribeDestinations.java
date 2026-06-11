package com.eventore.connector.spi;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SubscribeDestinations {

    private SubscribeDestinations() {}

    public static List<String> resolve(SubscribeRequest request) {
        Objects.requireNonNull(request, "subscribe request");
        if (!request.getDestinations().isEmpty()) {
            return request.getDestinations().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .toList();
        }
        String topicsOpt = request.getOptions() != null ? request.getOptions().get("topics") : null;
        if (topicsOpt != null && !topicsOpt.isBlank()) {
            return Arrays.stream(topicsOpt.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            return List.of(request.getDestination());
        }
        throw new IllegalArgumentException("At least one destination/topic is required");
    }
}

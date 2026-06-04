package com.eventore.connector.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SubscribeDestinations {

    private SubscribeDestinations() {}

    public static List<String> resolve(SubscribeRequest request) {
        if (request.getDestinations() != null && !request.getDestinations().isEmpty()) {
            return request.getDestinations().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .toList();
        }
        String topicsOpt = request.getOptions().get("topics");
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

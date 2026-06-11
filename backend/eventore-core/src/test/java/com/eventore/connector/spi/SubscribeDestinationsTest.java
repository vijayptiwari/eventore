package com.eventore.connector.spi;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscribeDestinationsTest {

    @Test
    void resolvesExplicitDestinationsList() {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestinations(List.of("orders", "  ", "orders", "events"));

        assertEquals(List.of("orders", "events"), SubscribeDestinations.resolve(request));
    }

    @Test
    void resolvesTopicsOptionWhenDestinationsEmpty() {
        SubscribeRequest request = new SubscribeRequest();
        request.setOptions(java.util.Map.of("topics", "alpha, beta ,alpha"));

        assertEquals(List.of("alpha", "beta"), SubscribeDestinations.resolve(request));
    }

    @Test
    void resolvesSingleDestination() {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("my-topic");

        assertEquals(List.of("my-topic"), SubscribeDestinations.resolve(request));
    }

    @Test
    void prefersDestinationsOverOptionsAndSingleDestination() {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("ignored");
        request.setOptions(java.util.Map.of("topics", "also-ignored"));
        request.setDestinations(List.of("primary"));

        assertEquals(List.of("primary"), SubscribeDestinations.resolve(request));
    }

    @Test
    void throwsWhenRequestIsNull() {
        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> SubscribeDestinations.resolve(null));
        assertEquals("subscribe request", ex.getMessage());
    }

    @Test
    void throwsWhenNoDestinationProvided() {
        SubscribeRequest request = new SubscribeRequest();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> SubscribeDestinations.resolve(request));
        assertEquals("At least one destination/topic is required", ex.getMessage());
    }
}

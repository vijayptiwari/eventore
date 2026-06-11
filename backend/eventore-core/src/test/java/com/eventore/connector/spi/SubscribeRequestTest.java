package com.eventore.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubscribeRequestTest {

    @Test
    void nullOptionsReturnsEmptyMap() {
        SubscribeRequest request = new SubscribeRequest();
        request.setOptions(null);
        assertThat(request.getOptions()).isEmpty();
    }

    @Test
    void nullDestinationsReturnsEmptyList() {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestinations(null);
        assertThat(request.getDestinations()).isEmpty();
    }

    @Test
    void setDestinationsCopiesProvidedList() {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestinations(List.of("topic-a", "topic-b"));
        assertThat(request.getDestinations()).containsExactly("topic-a", "topic-b");
    }

    @Test
    void setOptionsCopiesProvidedMap() {
        SubscribeRequest request = new SubscribeRequest();
        request.setOptions(Map.of("qos", "1"));
        assertThat(request.getOptions()).containsEntry("qos", "1");
    }
}

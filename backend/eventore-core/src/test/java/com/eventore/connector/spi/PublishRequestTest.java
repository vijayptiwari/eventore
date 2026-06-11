package com.eventore.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishRequestTest {

    @Test
    void nullHeadersReturnsEmptyMap() {
        PublishRequest request = new PublishRequest();
        request.setHeaders(null);
        assertThat(request.getHeaders()).isEmpty();
    }

    @Test
    void setHeadersCopiesProvidedMap() {
        PublishRequest request = new PublishRequest();
        request.setHeaders(Map.of("k", "v"));
        assertThat(request.getHeaders()).containsEntry("k", "v");
    }
}

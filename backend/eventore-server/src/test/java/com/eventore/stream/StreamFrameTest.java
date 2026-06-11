package com.eventore.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamFrameTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsJsonWithOptionalClientStreamId() throws Exception {
        StreamFrame frame = new StreamFrame("SUBSCRIBED", "sub-1", "client-1", null, null);
        String json = objectMapper.writeValueAsString(frame);
        StreamFrame parsed = objectMapper.readValue(json, StreamFrame.class);
        assertEquals("SUBSCRIBED", parsed.getType());
        assertEquals("sub-1", parsed.getSubscriptionId());
        assertEquals("client-1", parsed.getClientStreamId());
        assertNull(parsed.getMessage());
    }
}

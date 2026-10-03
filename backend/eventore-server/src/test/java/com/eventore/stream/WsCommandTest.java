package com.eventore.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsCommandTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesSubscribeCommandFields() throws Exception {
        String json =
                """
                {
                  "type": "SUBSCRIBE",
                  "clientStreamId": "stream-1",
                  "connectionId": "conn-1",
                  "destination": "orders",
                  "consumerGroup": "cg-1",
                  "topics": ["orders", "payments"]
                }
                """;
        WsCommand command = objectMapper.readValue(json, WsCommand.class);
        assertEquals("SUBSCRIBE", command.getType());
        assertEquals("stream-1", command.getClientStreamId());
        assertEquals("conn-1", command.getConnectionId());
        assertEquals("orders", command.getDestination());
        assertEquals("cg-1", command.getConsumerGroup());
        assertEquals(List.of("orders", "payments"), command.getTopics());
    }

    @Test
    void validateTypeRejectsNullType() {
        WsCommand command = new WsCommand();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> WsCommand.validateType(command));
        assertEquals("WebSocket command type is required", ex.getMessage());
    }

    @Test
    void validateTypeRejectsBlankType() throws Exception {
        WsCommand command = objectMapper.readValue("{\"type\":\"   \"}", WsCommand.class);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> WsCommand.validateType(command));
        assertEquals("WebSocket command type is required", ex.getMessage());
    }

    @Test
    void deserializesReplayFields() throws Exception {
        String json =
                """
                {
                  "type": "START_LIVE_VIEW",
                  "clientStreamId": "stream-2",
                  "connectionId": "conn-1",
                  "topics": ["orders"],
                  "replayMode": "TIMESTAMP",
                  "replayTimestamp": "2026-10-03T18:00:00Z",
                  "replayOffset": 12345
                }
                """;
        WsCommand command = objectMapper.readValue(json, WsCommand.class);
        assertEquals("START_LIVE_VIEW", command.getType());
        assertEquals("TIMESTAMP", command.getReplayMode());
        assertEquals("2026-10-03T18:00:00Z", command.getReplayTimestamp());
        assertEquals(12345L, command.getReplayOffset());
    }
}

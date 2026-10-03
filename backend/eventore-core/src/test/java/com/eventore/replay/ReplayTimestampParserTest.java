package com.eventore.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReplayTimestampParserTest {

    @Test
    void parsesEpochMillis() {
        long expected = 1727978400000L;
        long actual = ReplayTimestampParser.parseEpochMillis("1727978400000");
        assertEquals(expected, actual);
    }

    @Test
    void parsesIso8601Instant() {
        String iso = "2026-10-03T18:00:00Z";
        long expected = Instant.parse(iso).toEpochMilli();
        long actual = ReplayTimestampParser.parseEpochMillis(iso);
        assertEquals(expected, actual);
    }

    @Test
    void parsesLocalDateTime() {
        String local = "2026-10-03T18:00:00";
        long actual = ReplayTimestampParser.parseEpochMillis(local);
        // Should parse successfully without error
        org.junit.jupiter.api.Assertions.assertTrue(actual > 0);
    }

    @Test
    void throwsOnInvalidOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> ReplayTimestampParser.parseEpochMillis(null));
        assertThrows(IllegalArgumentException.class, () -> ReplayTimestampParser.parseEpochMillis(""));
        assertThrows(IllegalArgumentException.class, () -> ReplayTimestampParser.parseEpochMillis("not-a-date"));
    }
}

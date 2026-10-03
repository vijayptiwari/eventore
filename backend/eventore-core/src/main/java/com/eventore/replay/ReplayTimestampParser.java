package com.eventore.replay;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for parsing flexible timestamp inputs for time-travel message replay and search.
 * Supports epoch milliseconds, ISO-8601 instant strings, and ISO local date-time.
 */
public final class ReplayTimestampParser {

    private ReplayTimestampParser() {}

    /**
     * Parses a string representation of a timestamp into epoch milliseconds.
     *
     * @param input raw input string (epoch ms, ISO-8601, or local ISO datetime)
     * @return epoch milliseconds
     * @throws IllegalArgumentException if the timestamp cannot be parsed
     */
    public static long parseEpochMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Timestamp input cannot be null or blank");
        }
        String s = input.trim();

        // 1. Pure digits -> epoch milliseconds
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            // Not a pure integer, proceed to ISO parsers
        }

        // 2. Standard ISO-8601 with timezone/offset (e.g. 2026-10-03T18:00:00Z or +00:00)
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
            // Not a standard Instant, try LocalDateTime
        }

        // 3. Local date-time without timezone (e.g. from HTML datetime-local input: 2026-10-03T18:00:00 or 2026-10-03T18:00)
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid timestamp format '" + input + "'. Expected epoch millis or ISO-8601 string.", e);
        }
    }
}

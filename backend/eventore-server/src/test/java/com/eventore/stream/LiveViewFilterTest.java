package com.eventore.stream;

import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveViewFilterTest {

    @Test
    void compiledFilterMatchesPayloadAndHeaders() {
        UnifiedMessage message = new UnifiedMessage();
        message.setProtocol(ProtocolType.KAFKA);
        message.setDirection(MessageDirection.INBOUND);
        message.setPayload("order-123 confirmed");
        message.putHeader("event", "ORDER_CREATED");

        LiveViewFilter.Compiled compiled = LiveViewFilter.compile("ORDER_", "order-\\d+");

        assertTrue(LiveViewFilter.matches(compiled, message));
    }

    @Test
    void rejectsWhenBodyDoesNotMatch() {
        UnifiedMessage message = new UnifiedMessage();
        message.setPayload("nothing here");

        assertFalse(LiveViewFilter.matches(LiveViewFilter.compile(null, "secret"), message));
    }

    @Test
    void rejectsOverlongRegex() {
        assertThrows(IllegalArgumentException.class, () -> LiveViewFilter.compile("a".repeat(600), null));
    }

    @Test
    void acceptsRegexAtExactlyMaxLengthAndRejectsOneOver() {
        String atLimit = "a".repeat(512);
        LiveViewFilter.Compiled compiled = LiveViewFilter.compile(atLimit, atLimit);
        assertTrue(compiled.headerPattern() != null && compiled.bodyPattern() != null);

        String oneOver = "a".repeat(513);
        assertThrows(IllegalArgumentException.class, () -> LiveViewFilter.compile(oneOver, null));
        assertThrows(IllegalArgumentException.class, () -> LiveViewFilter.compile(null, oneOver));
    }

    @Test
    void rejectsInvalidRegexSyntax() {
        assertThrows(IllegalArgumentException.class, () -> LiveViewFilter.compile("[unclosed", null));
    }
}

package com.eventore.connector.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionKeysTest {

    @Test
    void matchesExactConnectionId() {
        assertTrue(SubscriptionKeys.belongsToConnection("conn-1", "conn-1"));
    }

    @Test
    void matchesScopedSubscriptionKey() {
        assertTrue(SubscriptionKeys.belongsToConnection("conn-1:orders", "conn-1"));
    }

    @Test
    void rejectsPrefixCollision() {
        assertFalse(SubscriptionKeys.belongsToConnection("conn-10:orders", "conn-1"));
    }

    @Test
    void rejectsNullOrBlank() {
        assertFalse(SubscriptionKeys.belongsToConnection(null, "conn-1"));
        assertFalse(SubscriptionKeys.belongsToConnection("conn-1:orders", null));
        assertFalse(SubscriptionKeys.belongsToConnection("conn-1:orders", "  "));
    }
}

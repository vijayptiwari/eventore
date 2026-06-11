package com.eventore.connector.spi;

public final class SubscriptionKeys {

    private SubscriptionKeys() {}

    /** Matches exact connection id or keys scoped as {@code connectionId:...}. */
    public static boolean belongsToConnection(String subscriptionKey, String connectionId) {
        if (subscriptionKey == null || connectionId == null || connectionId.isBlank()) {
            return false;
        }
        return subscriptionKey.equals(connectionId) || subscriptionKey.startsWith(connectionId + ":");
    }
}

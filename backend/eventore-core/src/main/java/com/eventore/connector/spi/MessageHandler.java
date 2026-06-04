package com.eventore.connector.spi;

import com.eventore.domain.UnifiedMessage;

@FunctionalInterface
public interface MessageHandler {
    void onMessage(UnifiedMessage message);

    default void onError(String message) {
        // optional
    }
}

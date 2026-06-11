package com.eventore.connector.spi;

import com.eventore.domain.UnifiedMessage;

/**
 * Callback invoked for each message delivered by a connector subscription.
 */
@FunctionalInterface
public interface MessageHandler {
    void onMessage(UnifiedMessage message);

    /**
     * Called when the connector reports a non-fatal delivery or processing error.
     * The default implementation is intentionally a no-op so simple handlers (e.g.
     * lambdas) need not implement error reporting. Production handlers should
     * override this method to log, meter, or surface errors appropriately.
     */
    default void onError(String message) {}
}

package com.eventore.provider;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import java.util.Optional;

/**
 * Pluggable stream provider module (one implementation per protocol).
 * Registered via Spring Boot auto-configuration in each {@code eventore-provider-*} module.
 */
public interface StreamProvider {

    ProtocolType protocol();

    MessagingConnector connector();

    default Optional<MessagingInspector> inspector() {
        return Optional.empty();
    }

    /** Provider module id, e.g. {@code kafka}, {@code gcp-pubsub}. */
    default String moduleId() {
        return protocol().name().toLowerCase().replace('_', '-');
    }
}

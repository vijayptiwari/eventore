package com.eventore.provider;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(StreamProviderRegistry.class);

    private final Map<ProtocolType, StreamProvider> providers = new EnumMap<>(ProtocolType.class);

    public StreamProviderRegistry(List<StreamProvider> providerList) {
        for (StreamProvider provider : providerList) {
            StreamProvider previous = providers.putIfAbsent(provider.protocol(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate stream provider for protocol " + provider.protocol()
                                + ": " + previous.moduleId() + " and " + provider.moduleId());
            }
            log.info("Registered stream provider {} (module={})", provider.protocol(), provider.moduleId());
        }
    }

    public StreamProvider get(ProtocolType protocol) {
        StreamProvider provider = providers.get(protocol);
        if (provider == null) {
            throw new IllegalArgumentException("No stream provider registered for " + protocol);
        }
        return provider;
    }

    public MessagingConnector connector(ProtocolType protocol) {
        return get(protocol).connector();
    }

    public MessagingInspector inspector(ProtocolType protocol) {
        return get(protocol)
                .inspector()
                .orElseThrow(() -> new IllegalArgumentException("No inspector for " + protocol));
    }

    public Optional<MessagingInspector> inspectorOptional(ProtocolType protocol) {
        return Optional.ofNullable(providers.get(protocol)).flatMap(StreamProvider::inspector);
    }

    public Map<ProtocolType, StreamProvider> all() {
        return Map.copyOf(providers);
    }

    public boolean isSupported(ProtocolType protocol) {
        return providers.containsKey(protocol);
    }
}

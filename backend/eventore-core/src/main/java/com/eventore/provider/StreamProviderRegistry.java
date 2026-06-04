package com.eventore.provider;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StreamProviderRegistry {

    private final Map<ProtocolType, StreamProvider> providers = new EnumMap<>(ProtocolType.class);

    public StreamProviderRegistry(List<StreamProvider> providerList) {
        for (StreamProvider provider : providerList) {
            providers.put(provider.protocol(), provider);
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

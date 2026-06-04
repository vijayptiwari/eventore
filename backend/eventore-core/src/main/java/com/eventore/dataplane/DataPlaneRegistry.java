package com.eventore.dataplane;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.StreamProvider;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data-plane registry: runtime {@link StreamProvider} handles used for broker I/O.
 * Only protocols registered in the {@link ControlPlaneRegistry} may be resolved.
 */
public class DataPlaneRegistry {

    private final ControlPlaneRegistry controlPlane;
    private final Map<ProtocolType, StreamProvider> providers = new EnumMap<>(ProtocolType.class);

    public DataPlaneRegistry(ControlPlaneRegistry controlPlane) {
        this.controlPlane = controlPlane;
    }

    public void attach(ProtocolType protocol, StreamProvider provider) {
        providers.put(protocol, provider);
    }

    public void detach(ProtocolType protocol) {
        providers.remove(protocol);
    }

    public StreamProvider requireProvider(ProtocolType protocol) {
        if (!controlPlane.isRegistered(protocol)) {
            throw new DataPlaneException(
                    "Protocol " + protocol + " is not registered in the control plane");
        }
        StreamProvider provider = providers.get(protocol);
        if (provider == null) {
            throw new DataPlaneException("No data-plane provider implementation for " + protocol);
        }
        return provider;
    }

    public MessagingConnector connector(ProtocolType protocol) {
        return requireProvider(protocol).connector();
    }

    public MessagingInspector inspector(ProtocolType protocol) {
        return requireProvider(protocol)
                .inspector()
                .orElseThrow(() -> new DataPlaneException("No inspector for " + protocol));
    }

    public Optional<MessagingInspector> inspectorOptional(ProtocolType protocol) {
        if (!controlPlane.isRegistered(protocol)) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(protocol)).flatMap(StreamProvider::inspector);
    }

    public Map<ProtocolType, StreamProvider> attachedProviders() {
        return Map.copyOf(providers);
    }

    public boolean canRoute(ProtocolType protocol) {
        return controlPlane.isRegistered(protocol) && providers.containsKey(protocol);
    }
}

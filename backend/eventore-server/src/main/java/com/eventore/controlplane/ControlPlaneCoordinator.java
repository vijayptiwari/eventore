package com.eventore.controlplane;

import com.eventore.dataplane.DataPlaneRegistry;
import com.eventore.domain.ProtocolType;
import com.eventore.provider.StreamProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates control-plane registration and data-plane attachment (desired state vs runtime handles).
 */
@Service
public class ControlPlaneCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneCoordinator.class);

    private final ControlPlaneRegistry controlPlane;
    private final DataPlaneRegistry dataPlane;
    private final Map<ProtocolType, StreamProvider> availableImplementations = new ConcurrentHashMap<>();

    public ControlPlaneCoordinator(ControlPlaneRegistry controlPlane, DataPlaneRegistry dataPlane) {
        this.controlPlane = controlPlane;
        this.dataPlane = dataPlane;
    }

    public void indexImplementation(StreamProvider provider) {
        availableImplementations.put(provider.protocol(), provider);
    }

    public StreamProviderDescriptor register(ProtocolType protocol) {
        StreamProvider implementation = availableImplementations.get(protocol);
        if (implementation == null) {
            throw new IllegalArgumentException(
                    "No provider implementation on classpath for " + protocol);
        }
        if (controlPlane.isRegistered(protocol)) {
            log.info("Provider {} already registered in control plane", protocol);
            return controlPlane.find(protocol).orElseThrow();
        }
        StreamProviderDescriptor descriptor = StreamProviderDescriptorFactory.fromProvider(implementation);
        StreamProviderDescriptor registered = controlPlane.register(descriptor);
        dataPlane.attach(protocol, implementation);
        log.info("Control plane: registered {} (module={})", protocol, registered.getModuleId());
        return registered;
    }

    public StreamProviderDescriptor deregister(ProtocolType protocol) {
        if (!controlPlane.isRegistered(protocol)) {
            throw new IllegalArgumentException("Provider not registered: " + protocol);
        }
        dataPlane.detach(protocol);
        StreamProviderDescriptor deregistered = controlPlane.deregister(protocol);
        log.info("Control plane: deregistered {}", protocol);
        return deregistered;
    }

    public ControlPlaneSnapshot snapshot() {
        return controlPlane.snapshot();
    }

    public boolean canRegister(ProtocolType protocol) {
        return availableImplementations.containsKey(protocol);
    }
}

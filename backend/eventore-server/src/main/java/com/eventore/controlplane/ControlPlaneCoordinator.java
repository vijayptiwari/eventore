package com.eventore.controlplane;

import com.eventore.dataplane.DataPlaneRegistry;
import com.eventore.domain.ProtocolType;
import com.eventore.provider.StreamProvider;
import com.eventore.service.AuditService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates control-plane registration and data-plane attachment (desired state vs runtime handles).
 */
@Service
public class ControlPlaneCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneCoordinator.class);

    private final ControlPlaneRegistry controlPlane;
    private final DataPlaneRegistry dataPlane;
    private final AuditService auditService;
    private final Map<ProtocolType, StreamProvider> availableImplementations = new ConcurrentHashMap<>();
    /** Serializes register/deregister so the last-active-provider guard is atomic. */
    private final Object lifecycleLock = new Object();

    public ControlPlaneCoordinator(
            ControlPlaneRegistry controlPlane, DataPlaneRegistry dataPlane, AuditService auditService) {
        this.controlPlane = controlPlane;
        this.dataPlane = dataPlane;
        this.auditService = auditService;
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
        synchronized (lifecycleLock) {
            if (controlPlane.isRegistered(protocol)) {
                log.info("Provider {} already registered in control plane", protocol);
                return controlPlane.find(protocol).orElseThrow();
            }
            StreamProviderDescriptor descriptor = StreamProviderDescriptorFactory.fromProvider(implementation);
            StreamProviderDescriptor registered = controlPlane.register(descriptor);
            dataPlane.attach(protocol, implementation);
            log.info("Control plane: registered {} (module={})", protocol, registered.getModuleId());
            auditService.providerRegistered(protocol);
            return registered;
        }
    }

    public StreamProviderDescriptor deregister(ProtocolType protocol) {
        synchronized (lifecycleLock) {
            if (!controlPlane.isRegistered(protocol)) {
                throw new IllegalArgumentException("Provider not registered: " + protocol);
            }
            if (controlPlane.listRegistered().size() <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Cannot deregister the last active provider");
            }
            dataPlane.detach(protocol);
            StreamProviderDescriptor deregistered = controlPlane.deregister(protocol);
            log.info("Control plane: deregistered {}", protocol);
            auditService.providerDeregistered(protocol);
            return deregistered;
        }
    }

    public ControlPlaneSnapshot snapshot() {
        return controlPlane.snapshot();
    }

    public boolean canRegister(ProtocolType protocol) {
        return availableImplementations.containsKey(protocol);
    }
}

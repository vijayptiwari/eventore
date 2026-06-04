package com.eventore.controlplane;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.ProtocolType;
import com.eventore.provider.StreamProvider;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ControlPlaneBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneBootstrap.class);

    private final ControlPlaneCoordinator coordinator;
    private final EventoreProperties properties;
    private final List<StreamProvider> discoveredProviders;

    public ControlPlaneBootstrap(
            ControlPlaneCoordinator coordinator,
            EventoreProperties properties,
            List<StreamProvider> discoveredProviders) {
        this.coordinator = coordinator;
        this.properties = properties;
        this.discoveredProviders = discoveredProviders;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapProviders() {
        if (!properties.getControlPlane().isAutoRegisterOnStartup()) {
            log.warn("Control plane auto-register disabled; register providers via /api/v1/control/providers");
            return;
        }
        Set<ProtocolType> toRegister = resolveProtocolsToRegister();
        for (StreamProvider provider : discoveredProviders) {
            coordinator.indexImplementation(provider);
        }
        for (ProtocolType protocol : toRegister) {
            if (coordinator.canRegister(protocol)) {
                coordinator.register(protocol);
            }
        }
        if (coordinator.snapshot().getProviders().isEmpty()) {
            throw new IllegalStateException(
                    "Control plane has no registered providers. Check eventore.enabled-protocols and provider modules.");
        }
        log.info(
                "Control plane bootstrap complete: {}",
                coordinator.snapshot().getActiveProtocols());
    }

    private Set<ProtocolType> resolveProtocolsToRegister() {
        String raw = properties.getEnabledProtocolsRaw();
        Set<ProtocolType> onClasspath = discoveredProviders.stream()
                .map(StreamProvider::protocol)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProtocolType.class)));
        if (raw == null || raw.isBlank()) {
            return onClasspath;
        }
        Set<ProtocolType> configured = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> ProtocolType.valueOf(s.toUpperCase()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProtocolType.class)));
        configured.retainAll(onClasspath);
        return configured;
    }
}

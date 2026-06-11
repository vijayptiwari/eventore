package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultControlPlaneRegistry implements ControlPlaneRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultControlPlaneRegistry.class);

    private final Map<ProtocolType, StreamProviderDescriptor> descriptors = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong(0);

    @Override
    public StreamProviderDescriptor register(StreamProviderDescriptor descriptor) {
        StreamProviderDescriptor copy = copy(descriptor);
        copy.setState(ProviderLifecycleState.REGISTERED);
        copy.setRegisteredAt(Instant.now());
        copy.setDeregisteredAt(null);
        // compute() makes the revision bump and the map mutation atomic per protocol.
        StreamProviderDescriptor registered = descriptors.compute(descriptor.getProtocol(), (protocol, existing) -> {
            copy.setRegistrationEpoch(revision.incrementAndGet());
            return copy;
        });
        log.info("Registered control-plane provider {} (module={})", registered.getProtocol(), registered.getModuleId());
        return registered;
    }

    @Override
    public StreamProviderDescriptor deregister(ProtocolType protocol) {
        StreamProviderDescriptor updated = descriptors.computeIfPresent(protocol, (p, existing) -> {
            StreamProviderDescriptor copy = copy(existing);
            copy.setState(ProviderLifecycleState.DEREGISTERED);
            copy.setDeregisteredAt(Instant.now());
            copy.setRegistrationEpoch(revision.incrementAndGet());
            return copy;
        });
        if (updated == null) {
            throw new IllegalArgumentException("Provider not registered: " + protocol);
        }
        log.info("Deregistered control-plane provider {} (module={})", protocol, updated.getModuleId());
        return updated;
    }

    @Override
    public Optional<StreamProviderDescriptor> find(ProtocolType protocol) {
        return Optional.ofNullable(descriptors.get(protocol));
    }

    @Override
    public List<StreamProviderDescriptor> list() {
        return descriptors.values().stream()
                .sorted((a, b) -> a.getProtocol().name().compareTo(b.getProtocol().name()))
                .map(DefaultControlPlaneRegistry::copy)
                .collect(Collectors.toList());
    }

    @Override
    public List<StreamProviderDescriptor> listRegistered() {
        return list().stream().filter(StreamProviderDescriptor::isActive).toList();
    }

    @Override
    public Set<ProtocolType> registeredProtocols() {
        Set<ProtocolType> active = EnumSet.noneOf(ProtocolType.class);
        descriptors.values().stream()
                .filter(StreamProviderDescriptor::isActive)
                .forEach(d -> active.add(d.getProtocol()));
        return Set.copyOf(active);
    }

    @Override
    public boolean isRegistered(ProtocolType protocol) {
        return find(protocol).map(StreamProviderDescriptor::isActive).orElse(false);
    }

    @Override
    public long revision() {
        return revision.get();
    }

    @Override
    public ControlPlaneSnapshot snapshot() {
        List<StreamProviderDescriptor> registered = listRegistered();
        ControlPlaneSnapshot snapshot = new ControlPlaneSnapshot();
        snapshot.setRevision(revision.get());
        snapshot.setProviders(registered);
        snapshot.setActiveProtocols(registered.stream()
                .map(d -> d.getProtocol().name())
                .toList());
        snapshot.setOpenApiStreams(registered.stream()
                .map(StreamProviderDescriptor::getOpenApiStreamId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList());

        ControlPlaneSnapshot.UiCascade cascade = new ControlPlaneSnapshot.UiCascade();
        cascade.setConnectionProtocols(new ArrayList<>(snapshot.getActiveProtocols()));
        cascade.setPlatformFilterProtocols(new ArrayList<>(snapshot.getActiveProtocols()));
        cascade.setInspectProtocols(registered.stream()
                .filter(d -> d.getCapabilities() != null && d.getCapabilities().isInspect())
                .map(d -> d.getProtocol().name())
                .toList());
        cascade.setAdminProtocols(registered.stream()
                .filter(d -> d.getCapabilities() != null && d.getCapabilities().isAdmin())
                .map(d -> d.getProtocol().name())
                .toList());
        snapshot.setUiCascade(cascade);
        return snapshot;
    }

    private static StreamProviderDescriptor copy(StreamProviderDescriptor source) {
        StreamProviderDescriptor d = new StreamProviderDescriptor();
        d.setProtocol(source.getProtocol());
        d.setModuleId(source.getModuleId());
        d.setConnectorClass(source.getConnectorClass());
        d.setState(source.getState());
        d.setOpenApiStreamId(source.getOpenApiStreamId());
        d.setRegistrationEpoch(source.getRegistrationEpoch());
        d.setRegisteredAt(source.getRegisteredAt());
        d.setDeregisteredAt(source.getDeregisteredAt());
        if (source.getCapabilities() != null) {
            ProviderCapabilities caps = new ProviderCapabilities();
            caps.setMessaging(source.getCapabilities().isMessaging());
            caps.setInspect(source.getCapabilities().isInspect());
            caps.setAdmin(source.getCapabilities().isAdmin());
            caps.setLiveView(source.getCapabilities().isLiveView());
            caps.setDataPlaneApiPrefixes(new ArrayList<>(source.getCapabilities().getDataPlaneApiPrefixes()));
            d.setCapabilities(caps);
        }
        return d;
    }
}

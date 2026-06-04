package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative registry of which stream providers are registered with the platform (control plane).
 * The data plane must only route traffic to protocols present here in REGISTERED state.
 */
public interface ControlPlaneRegistry {

    StreamProviderDescriptor register(StreamProviderDescriptor descriptor);

    StreamProviderDescriptor deregister(ProtocolType protocol);

    Optional<StreamProviderDescriptor> find(ProtocolType protocol);

    List<StreamProviderDescriptor> list();

    List<StreamProviderDescriptor> listRegistered();

    Set<ProtocolType> registeredProtocols();

    boolean isRegistered(ProtocolType protocol);

    long revision();

    ControlPlaneSnapshot snapshot();
}

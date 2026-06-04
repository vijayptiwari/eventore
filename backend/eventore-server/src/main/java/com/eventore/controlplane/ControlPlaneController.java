package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Control plane API: provider registration, deregistration, and UI cascade metadata.
 * Does not perform broker I/O.
 */
@RestController
@RequestMapping("/api/v1/control")
public class ControlPlaneController {

    private final ControlPlaneCoordinator coordinator;
    private final ControlPlaneRegistry controlPlane;
    private final DeploymentModePolicy policy;

    public ControlPlaneController(
            ControlPlaneCoordinator coordinator,
            ControlPlaneRegistry controlPlane,
            DeploymentModePolicy policy) {
        this.coordinator = coordinator;
        this.controlPlane = controlPlane;
        this.policy = policy;
    }

    @GetMapping("/plane")
    public ControlPlaneSnapshot plane() {
        return coordinator.snapshot();
    }

    @GetMapping("/providers")
    public List<StreamProviderDescriptor> listProviders() {
        return controlPlane.list();
    }

    @GetMapping("/providers/{protocol}")
    public StreamProviderDescriptor getProvider(@PathVariable ProtocolType protocol) {
        return controlPlane
                .find(protocol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/providers/{protocol}/register")
    public StreamProviderDescriptor register(@PathVariable ProtocolType protocol) {
        policy.require(Action.ADMIN_BROKER_OPS);
        if (!coordinator.canRegister(protocol)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Provider implementation not available: " + protocol);
        }
        policy.requireProtocol(protocol);
        return coordinator.register(protocol);
    }

    @DeleteMapping("/providers/{protocol}/register")
    public StreamProviderDescriptor deregister(@PathVariable ProtocolType protocol) {
        policy.require(Action.ADMIN_BROKER_OPS);
        if (controlPlane.listRegistered().size() <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Cannot deregister the last active provider");
        }
        return coordinator.deregister(protocol);
    }

    @GetMapping("/providers/{protocol}/status")
    public Map<String, Object> status(@PathVariable ProtocolType protocol) {
        StreamProviderDescriptor descriptor = controlPlane
                .find(protocol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of(
                "protocol", protocol.name(),
                "state", descriptor.getState().name(),
                "registered", descriptor.isActive(),
                "dataPlaneRoutable", descriptor.isActive() && coordinator.canRegister(protocol),
                "revision", controlPlane.revision());
    }
}

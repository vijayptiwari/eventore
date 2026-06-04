package com.eventore.security;

import com.eventore.config.EventoreProperties;
import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.domain.ProtocolType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeploymentModePolicy {

    private final EventoreProperties properties;
    private final ControlPlaneRegistry controlPlane;

    public DeploymentModePolicy(EventoreProperties properties, ControlPlaneRegistry controlPlane) {
        this.properties = properties;
        this.controlPlane = controlPlane;
    }

    public DeploymentMode mode() {
        return properties.getDeploymentMode();
    }

    public List<Action> allowedActions() {
        return switch (mode()) {
            case ADMIN -> List.of(
                    Action.MANAGE_CONNECTIONS,
                    Action.BROWSE_DESTINATIONS,
                    Action.SUBSCRIBE,
                    Action.PUBLISH,
                    Action.ADMIN_BROKER_OPS);
            case DEV -> List.of(
                    Action.MANAGE_CONNECTIONS,
                    Action.BROWSE_DESTINATIONS,
                    Action.SUBSCRIBE,
                    Action.PUBLISH);
            case READONLY -> List.of(Action.BROWSE_DESTINATIONS, Action.SUBSCRIBE);
        };
    }

    public void require(Action action) {
        if (!allowedActions().contains(action)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Action " + action + " not allowed in " + mode() + " deployment");
        }
    }

    public void requireProtocol(ProtocolType protocol) {
        if (!controlPlane.isRegistered(protocol)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Protocol " + protocol + " is not registered in the control plane");
        }
        if (mode() == DeploymentMode.DEV) {
            Set<ProtocolType> allowed = properties.getDev().getAllowedProtocols();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(protocol)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Protocol " + protocol + " not allowed in DEV deployment");
            }
        }
    }

    public void validatePublishSize(long bytes) {
        if (mode() == DeploymentMode.DEV && bytes > properties.getDev().getMaxPublishBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Publish exceeds DEV max size of " + properties.getDev().getMaxPublishBytes());
        }
    }

    /** Intersection of control-plane registration and DEV allow-list. */
    public Set<ProtocolType> supportedProtocols() {
        Set<ProtocolType> deployed = EnumSet.copyOf(controlPlane.registeredProtocols());
        if (mode() == DeploymentMode.DEV) {
            Set<ProtocolType> allowed = properties.getDev().getAllowedProtocols();
            if (allowed != null && !allowed.isEmpty()) {
                deployed.retainAll(allowed);
            }
        }
        return deployed;
    }

    public List<String> allowedActionsAsStrings() {
        List<String> names = new ArrayList<>();
        for (Action action : allowedActions()) {
            names.add(action.name());
        }
        return names;
    }
}

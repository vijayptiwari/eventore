package com.eventore.api.dto;

import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppConfigResponse {

    private DeploymentMode deploymentMode;
    private List<String> allowedActions;
    private Set<ProtocolType> supportedProtocols;
    private List<String> loadedModules;
    private ControlPlaneView controlPlane;

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public List<String> getAllowedActions() {
        return allowedActions != null ? Collections.unmodifiableList(allowedActions) : List.of();
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions != null ? new ArrayList<>(allowedActions) : null;
    }

    public Set<ProtocolType> getSupportedProtocols() {
        return supportedProtocols != null ? Collections.unmodifiableSet(supportedProtocols) : Set.of();
    }

    public void setSupportedProtocols(Set<ProtocolType> supportedProtocols) {
        this.supportedProtocols = supportedProtocols != null ? new HashSet<>(supportedProtocols) : null;
    }

    public List<String> getLoadedModules() {
        return loadedModules != null ? Collections.unmodifiableList(loadedModules) : List.of();
    }

    public void setLoadedModules(List<String> loadedModules) {
        this.loadedModules = loadedModules != null ? new ArrayList<>(loadedModules) : null;
    }

    public ControlPlaneView getControlPlane() {
        return controlPlane;
    }

    public void setControlPlane(ControlPlaneView controlPlane) {
        this.controlPlane = controlPlane;
    }
}

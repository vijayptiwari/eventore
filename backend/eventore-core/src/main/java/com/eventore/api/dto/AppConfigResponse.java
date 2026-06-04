package com.eventore.api.dto;

import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentMode;
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
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public Set<ProtocolType> getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(Set<ProtocolType> supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public List<String> getLoadedModules() {
        return loadedModules;
    }

    public void setLoadedModules(List<String> loadedModules) {
        this.loadedModules = loadedModules;
    }

    public ControlPlaneView getControlPlane() {
        return controlPlane;
    }

    public void setControlPlane(ControlPlaneView controlPlane) {
        this.controlPlane = controlPlane;
    }
}

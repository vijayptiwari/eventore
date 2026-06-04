package com.eventore.api.delegate;

import com.eventore.api.dto.AppConfigResponse;
import com.eventore.api.dto.ControlPlaneView;
import com.eventore.api.generated.core.ConfigApiDelegate;
import com.eventore.controlplane.ControlPlaneCoordinator;
import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.security.DeploymentModePolicy;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoreConfigApiDelegateImpl implements ConfigApiDelegate {

    private final DeploymentModePolicy policy;
    private final ControlPlaneRegistry controlPlane;
    private final ControlPlaneCoordinator coordinator;

    public CoreConfigApiDelegateImpl(
            DeploymentModePolicy policy,
            ControlPlaneRegistry controlPlane,
            ControlPlaneCoordinator coordinator) {
        this.policy = policy;
        this.controlPlane = controlPlane;
        this.coordinator = coordinator;
    }

    @Override
    public ResponseEntity<AppConfigResponse> getConfig() {
        AppConfigResponse response = new AppConfigResponse();
        response.setDeploymentMode(policy.mode());
        response.setAllowedActions(policy.allowedActionsAsStrings());
        response.setSupportedProtocols(policy.supportedProtocols());
        List<String> modules = controlPlane.listRegistered().stream()
                .map(d -> d.getModuleId())
                .sorted()
                .toList();
        response.setLoadedModules(modules);
        response.setControlPlane(ControlPlaneView.from(coordinator.snapshot()));
        return ResponseEntity.ok(response);
    }
}

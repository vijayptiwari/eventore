package com.eventore.api.delegate;

import com.eventore.api.generated.core.PlatformsApiDelegate;
import com.eventore.platform.StreamPlatformCatalog;
import com.eventore.platform.StreamPlatformPreset;
import com.eventore.security.DeploymentModePolicy;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CorePlatformsApiDelegateImpl implements PlatformsApiDelegate {

    private final DeploymentModePolicy policy;

    public CorePlatformsApiDelegateImpl(DeploymentModePolicy policy) {
        this.policy = policy;
    }

    @Override
    public ResponseEntity<List<StreamPlatformPreset>> listPlatforms() {
        return ResponseEntity.ok(StreamPlatformCatalog.all().stream()
                .filter(p -> policy.supportedProtocols().contains(p.getProtocol()))
                .toList());
    }
}

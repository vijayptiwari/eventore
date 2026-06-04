package com.eventore.api.delegate;

import com.eventore.api.dto.ProviderInfoDto;
import com.eventore.api.generated.core.ProvidersApiDelegate;
import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.controlplane.StreamProviderDescriptor;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoreProvidersApiDelegateImpl implements ProvidersApiDelegate {

    private final ControlPlaneRegistry controlPlane;

    public CoreProvidersApiDelegateImpl(ControlPlaneRegistry controlPlane) {
        this.controlPlane = controlPlane;
    }

    @Override
    public ResponseEntity<List<ProviderInfoDto>> listProviders() {
        List<ProviderInfoDto> list = controlPlane.listRegistered().stream()
                .map(CoreProvidersApiDelegateImpl::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    private static ProviderInfoDto toDto(StreamProviderDescriptor d) {
        boolean hasInspector = d.getCapabilities() != null && d.getCapabilities().isInspect();
        return new ProviderInfoDto(
                d.getProtocol(),
                d.getModuleId(),
                hasInspector,
                d.getConnectorClass());
    }
}

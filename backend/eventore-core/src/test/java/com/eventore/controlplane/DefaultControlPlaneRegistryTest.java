package com.eventore.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.domain.ProtocolType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultControlPlaneRegistryTest {

    @Test
    void ac2_adminProtocolsIncludesKinesisWhenProviderHasAdminCapability() {
        DefaultControlPlaneRegistry registry = new DefaultControlPlaneRegistry();
        registry.register(descriptor(ProtocolType.KINESIS, true));
        registry.register(descriptor(ProtocolType.MQTT, false));

        ControlPlaneSnapshot snapshot = registry.snapshot();

        assertThat(snapshot.getUiCascade().getAdminProtocols()).containsExactly("KINESIS");
    }

    @Test
    void ac5_kafkaAdminCapabilityUnchangedInCascade() {
        DefaultControlPlaneRegistry registry = new DefaultControlPlaneRegistry();
        registry.register(descriptor(ProtocolType.KAFKA, true));

        assertThat(registry.snapshot().getUiCascade().getAdminProtocols()).containsExactly("KAFKA");
    }

    private static StreamProviderDescriptor descriptor(ProtocolType protocol, boolean admin) {
        StreamProviderDescriptor descriptor = new StreamProviderDescriptor();
        descriptor.setProtocol(protocol);
        descriptor.setModuleId(protocol.name().toLowerCase());
        descriptor.setConnectorClass("com.example.Connector");
        descriptor.setState(ProviderLifecycleState.REGISTERED);

        ProviderCapabilities caps = new ProviderCapabilities();
        caps.setMessaging(true);
        caps.setInspect(true);
        caps.setAdmin(admin);
        caps.setLiveView(true);
        caps.setDataPlaneApiPrefixes(
                List.of("/api/v1/connections/{connectionId}/inspect"));
        descriptor.setCapabilities(caps);
        return descriptor;
    }
}

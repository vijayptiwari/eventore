package com.eventore.security;

import com.eventore.config.EventoreProperties;
import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.domain.ProtocolType;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentModePolicyTest {

    @Mock
    private ControlPlaneRegistry controlPlane;

    private EventoreProperties props;
    private DeploymentModePolicy policy;

    @BeforeEach
    void setUp() {
        props = new EventoreProperties();
        policy = new DeploymentModePolicy(props, controlPlane);
    }

    @Test
    void readonlyDisallowsPublish() {
        props.setDeploymentMode(DeploymentMode.READONLY);
        assertThrows(ResponseStatusException.class, () -> policy.require(Action.PUBLISH));
        assertTrue(policy.allowedActions().contains(Action.SUBSCRIBE));
    }

    @Test
    void devRestrictsProtocol() {
        props.setDeploymentMode(DeploymentMode.DEV);
        props.getDev().setAllowedProtocols(EnumSet.of(ProtocolType.KAFKA));
        when(controlPlane.isRegistered(ProtocolType.MQTT)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> policy.requireProtocol(ProtocolType.MQTT));
    }

    @Test
    void adminAllowsBrokerOps() {
        props.setDeploymentMode(DeploymentMode.ADMIN);
        assertEquals(5, policy.allowedActions().size());
    }

    @Test
    void publishSizeLimitedInAllModes() {
        props.setDeploymentMode(DeploymentMode.ADMIN);
        props.setMaxPublishBytes(1024);

        assertThrows(ResponseStatusException.class, () -> policy.validatePublishSize(2048));
    }

    @Test
    void devUsesStricterPublishLimit() {
        props.setDeploymentMode(DeploymentMode.DEV);
        props.setMaxPublishBytes(10_000);
        props.getDev().setMaxPublishBytes(100);

        assertThrows(ResponseStatusException.class, () -> policy.validatePublishSize(200));
    }

    @Test
    void supportedProtocolsHandlesEmptyRegistrationWithoutCrashing() {
        // Regression: EnumSet.copyOf throws on empty sources; the guard must keep this safe.
        when(controlPlane.registeredProtocols()).thenReturn(java.util.Set.of());

        assertTrue(policy.supportedProtocols().isEmpty());
    }

    @Test
    void supportedProtocolsIntersectsDevAllowList() {
        props.setDeploymentMode(DeploymentMode.DEV);
        props.getDev().setAllowedProtocols(EnumSet.of(ProtocolType.KAFKA));
        when(controlPlane.registeredProtocols())
                .thenReturn(EnumSet.of(ProtocolType.KAFKA, ProtocolType.MQTT));

        assertEquals(EnumSet.of(ProtocolType.KAFKA), policy.supportedProtocols());
    }
}

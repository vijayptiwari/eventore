package com.eventore.security;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.ProtocolType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentModePolicyTest {

    @Test
    void readonlyDisallowsPublish() {
        EventoreProperties props = new EventoreProperties();
        props.setDeploymentMode(DeploymentMode.READONLY);
        DeploymentModePolicy policy = new DeploymentModePolicy(props);
        assertThrows(ResponseStatusException.class, () -> policy.require(Action.PUBLISH));
        assertTrue(policy.allowedActions().contains(Action.SUBSCRIBE));
    }

    @Test
    void devRestrictsProtocol() {
        EventoreProperties props = new EventoreProperties();
        props.setDeploymentMode(DeploymentMode.DEV);
        props.getDev().setAllowedProtocols(java.util.EnumSet.of(ProtocolType.KAFKA));
        DeploymentModePolicy policy = new DeploymentModePolicy(props);
        assertThrows(ResponseStatusException.class, () -> policy.requireProtocol(ProtocolType.MQTT));
    }

    @Test
    void adminAllowsBrokerOps() {
        EventoreProperties props = new EventoreProperties();
        props.setDeploymentMode(DeploymentMode.ADMIN);
        DeploymentModePolicy policy = new DeploymentModePolicy(props);
        assertEquals(5, policy.allowedActions().size());
    }
}

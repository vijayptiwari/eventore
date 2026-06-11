package com.eventore.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeploymentModeTest {

    @Test
    void allDeploymentModesExist() {
        assertThat(DeploymentMode.values())
                .containsExactly(DeploymentMode.ADMIN, DeploymentMode.DEV, DeploymentMode.READONLY);
    }
}

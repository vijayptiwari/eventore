package com.eventore.config;

import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.controlplane.DefaultControlPlaneRegistry;
import com.eventore.dataplane.DataPlaneRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ControlPlaneConfiguration {

    @Bean
    ControlPlaneRegistry controlPlaneRegistry() {
        return new DefaultControlPlaneRegistry();
    }

    @Bean
    DataPlaneRegistry dataPlaneRegistry(ControlPlaneRegistry controlPlaneRegistry) {
        return new DataPlaneRegistry(controlPlaneRegistry);
    }
}

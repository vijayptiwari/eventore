package com.eventore.config;

import com.eventore.security.DeploymentMode;
import com.eventore.domain.ProtocolType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventore")
public class EventoreProperties {

    private DeploymentMode deploymentMode = DeploymentMode.DEV;
    /**
     * Comma-separated protocols to activate (e.g. KAFKA,KINESIS). Empty = all provider modules present on
     * classpath. Must match Maven provider dependencies baked into the image.
     */
    private String enabledProtocols = "";
    private Subscriptions subscriptions = new Subscriptions();
    private Dev dev = new Dev();
    private ControlPlane controlPlane = new ControlPlane();
    private DataPlane dataPlane = new DataPlane();

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getEnabledProtocolsRaw() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String enabledProtocols) {
        this.enabledProtocols = enabledProtocols != null ? enabledProtocols : "";
    }

    public Subscriptions getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Subscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Dev getDev() {
        return dev;
    }

    public void setDev(Dev dev) {
        this.dev = dev;
    }

    public ControlPlane getControlPlane() {
        return controlPlane;
    }

    public void setControlPlane(ControlPlane controlPlane) {
        this.controlPlane = controlPlane;
    }

    public DataPlane getDataPlane() {
        return dataPlane;
    }

    public void setDataPlane(DataPlane dataPlane) {
        this.dataPlane = dataPlane;
    }

    public static class ControlPlane {
        private boolean autoRegisterOnStartup = true;

        public boolean isAutoRegisterOnStartup() {
            return autoRegisterOnStartup;
        }

        public void setAutoRegisterOnStartup(boolean autoRegisterOnStartup) {
            this.autoRegisterOnStartup = autoRegisterOnStartup;
        }
    }

    public static class DataPlane {
        private boolean requireControlPlaneRegistration = true;

        public boolean isRequireControlPlaneRegistration() {
            return requireControlPlaneRegistration;
        }

        public void setRequireControlPlaneRegistration(boolean requireControlPlaneRegistration) {
            this.requireControlPlaneRegistration = requireControlPlaneRegistration;
        }
    }

    public static class Subscriptions {
        private int maxConcurrent = 50;
        private int queueCapacity = 500;

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class Dev {
        private Set<ProtocolType> allowedProtocols = EnumSet.allOf(ProtocolType.class);
        private long maxPublishBytes = 1_048_576L;

        public Set<ProtocolType> getAllowedProtocols() {
            return allowedProtocols;
        }

        public void setAllowedProtocols(Set<ProtocolType> allowedProtocols) {
            this.allowedProtocols = allowedProtocols;
        }

        public long getMaxPublishBytes() {
            return maxPublishBytes;
        }

        public void setMaxPublishBytes(long maxPublishBytes) {
            this.maxPublishBytes = maxPublishBytes;
        }
    }
}

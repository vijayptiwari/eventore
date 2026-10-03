package com.eventore.config;

import com.eventore.domain.ProtocolType;
import com.eventore.masking.MaskingConfig;
import com.eventore.security.DeploymentMode;
import com.eventore.tracing.TracingConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventore")
public class EventoreProperties {

    private DeploymentMode deploymentMode = DeploymentMode.DEV;
    private Security security = new Security();
    /**
     * Comma-separated protocols to activate (e.g. KAFKA,KINESIS). Empty = all provider modules present on
     * classpath. Must match Maven provider dependencies baked into the image.
     */
    private String enabledProtocols = "";
    private long maxPublishBytes = 10_485_760L;
    private Connections connections = new Connections();
    private Subscriptions subscriptions = new Subscriptions();
    private Diagnostics diagnostics = new Diagnostics();
    private Dev dev = new Dev();
    private ControlPlane controlPlane = new ControlPlane();
    private DataPlane dataPlane = new DataPlane();
    private Masking masking = new Masking();
    private Tracing tracing = new Tracing();

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

    public long getMaxPublishBytes() {
        return maxPublishBytes;
    }

    public void setMaxPublishBytes(long maxPublishBytes) {
        if (maxPublishBytes < 1) {
            throw new IllegalArgumentException("maxPublishBytes must be at least 1");
        }
        this.maxPublishBytes = maxPublishBytes;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Security {
        /** Static API token. Empty = authentication disabled (dev only). */
        private String apiToken = "";
        /** Comma-separated allowed origins for CORS and WebSocket. "*" = all (dev only). */
        private String allowedOrigins = "*";

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken != null ? apiToken : "";
        }

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins =
                    allowedOrigins != null && !allowedOrigins.isBlank() ? allowedOrigins : "*";
        }

        public boolean isAuthEnabled() {
            return !apiToken.isBlank();
        }

        public String[] allowedOriginsArray() {
            return java.util.Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
    }

    public Connections getConnections() {
        return connections;
    }

    public void setConnections(Connections connections) {
        this.connections = connections;
    }

    public Subscriptions getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Subscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
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

    public Masking getMasking() {
        return masking;
    }

    public void setMasking(Masking masking) {
        this.masking = masking;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    public static class Masking {
        private boolean enabled = true;
        private String replacement = MaskingConfig.DEFAULT_REPLACEMENT;
        private List<String> customFields = new ArrayList<>();
        private boolean maskValues = true;
        private boolean maskHeaders = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public List<String> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(List<String> customFields) {
            this.customFields = customFields != null ? new ArrayList<>(customFields) : new ArrayList<>();
        }

        public boolean isMaskValues() {
            return maskValues;
        }

        public void setMaskValues(boolean maskValues) {
            this.maskValues = maskValues;
        }

        public boolean isMaskHeaders() {
            return maskHeaders;
        }

        public void setMaskHeaders(boolean maskHeaders) {
            this.maskHeaders = maskHeaders;
        }

        public MaskingConfig toConfig() {
            MaskingConfig config = new MaskingConfig();
            config.setEnabled(enabled);
            config.setReplacement(replacement);
            if (customFields != null) {
                for (String field : customFields) {
                    config.addSensitiveFieldPattern(field);
                }
            }
            config.setMaskValuesByPattern(maskValues);
            return config;
        }
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

    public static class Connections {
        private Persistence persistence = new Persistence();

        public Persistence getPersistence() {
            return persistence;
        }

        public void setPersistence(Persistence persistence) {
            this.persistence = persistence;
        }

        public static class Persistence {
            private boolean enabled = false;
            private com.eventore.domain.ConnectionStoreType type = com.eventore.domain.ConnectionStoreType.FILE;
            private String filePath = "/data/connections.json";
            private String tableName = "eventore_connection_profiles";
            private boolean optimisticLocking = true;
            private String crdDirectory = "/data/crds";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public com.eventore.domain.ConnectionStoreType getType() {
                return type;
            }

            public void setType(com.eventore.domain.ConnectionStoreType type) {
                this.type = type != null ? type : com.eventore.domain.ConnectionStoreType.FILE;
            }

            public String getFilePath() {
                return filePath;
            }

            public void setFilePath(String filePath) {
                this.filePath = filePath != null ? filePath : "/data/connections.json";
            }

            public String getTableName() {
                return tableName;
            }

            public void setTableName(String tableName) {
                this.tableName = tableName != null && !tableName.isBlank() ? tableName : "eventore_connection_profiles";
            }

            public boolean isOptimisticLocking() {
                return optimisticLocking;
            }

            public void setOptimisticLocking(boolean optimisticLocking) {
                this.optimisticLocking = optimisticLocking;
            }

            public String getCrdDirectory() {
                return crdDirectory;
            }

            public void setCrdDirectory(String crdDirectory) {
                this.crdDirectory = crdDirectory != null ? crdDirectory : "/data/crds";
            }
        }
    }

    public static class Diagnostics {
        private int errorSubscriptionThreshold = 5;

        public int getErrorSubscriptionThreshold() {
            return errorSubscriptionThreshold;
        }

        public void setErrorSubscriptionThreshold(int errorSubscriptionThreshold) {
            this.errorSubscriptionThreshold = errorSubscriptionThreshold;
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
            if (maxPublishBytes < 1) {
                throw new IllegalArgumentException("dev.maxPublishBytes must be at least 1");
            }
            this.maxPublishBytes = maxPublishBytes;
        }
    }

    public static class Tracing {
        private boolean enabled = true;
        private String viewerType = "JAEGER";
        private String urlTemplate = TracingConfig.DEFAULT_JAEGER_URL;
        private boolean injectOnPublish = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getViewerType() {
            return viewerType;
        }

        public void setViewerType(String viewerType) {
            this.viewerType = viewerType;
        }

        public String getUrlTemplate() {
            return urlTemplate;
        }

        public void setUrlTemplate(String urlTemplate) {
            this.urlTemplate = urlTemplate;
        }

        public boolean isInjectOnPublish() {
            return injectOnPublish;
        }

        public void setInjectOnPublish(boolean injectOnPublish) {
            this.injectOnPublish = injectOnPublish;
        }

        public TracingConfig toTracingConfig() {
            TracingConfig cfg = new TracingConfig();
            cfg.setEnabled(this.enabled);
            cfg.setViewerType(this.viewerType);
            if (this.urlTemplate != null && !this.urlTemplate.isBlank()) {
                cfg.setUrlTemplate(this.urlTemplate);
            }
            cfg.setInjectOnPublish(this.injectOnPublish);
            return cfg;
        }
    }
}

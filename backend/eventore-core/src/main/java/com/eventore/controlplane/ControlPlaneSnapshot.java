package com.eventore.controlplane;

import java.util.ArrayList;
import java.util.List;

/** Aggregated control-plane view for UI and gateway configuration. */
public class ControlPlaneSnapshot {

    private long revision;
    private List<StreamProviderDescriptor> providers = new ArrayList<>();
    private List<String> activeProtocols = new ArrayList<>();
    private List<String> openApiStreams = new ArrayList<>();
    private UiCascade uiCascade = new UiCascade();

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public List<StreamProviderDescriptor> getProviders() {
        return providers;
    }

    public void setProviders(List<StreamProviderDescriptor> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    public List<String> getActiveProtocols() {
        return activeProtocols;
    }

    public void setActiveProtocols(List<String> activeProtocols) {
        this.activeProtocols = activeProtocols != null ? activeProtocols : new ArrayList<>();
    }

    public List<String> getOpenApiStreams() {
        return openApiStreams;
    }

    public void setOpenApiStreams(List<String> openApiStreams) {
        this.openApiStreams = openApiStreams != null ? openApiStreams : new ArrayList<>();
    }

    public UiCascade getUiCascade() {
        return uiCascade;
    }

    public void setUiCascade(UiCascade uiCascade) {
        this.uiCascade = uiCascade != null ? uiCascade : new UiCascade();
    }

    public static class UiCascade {
        private List<String> connectionProtocols = new ArrayList<>();
        private List<String> inspectProtocols = new ArrayList<>();
        private List<String> adminProtocols = new ArrayList<>();
        private List<String> platformFilterProtocols = new ArrayList<>();

        public List<String> getConnectionProtocols() {
            return connectionProtocols;
        }

        public void setConnectionProtocols(List<String> connectionProtocols) {
            this.connectionProtocols = connectionProtocols;
        }

        public List<String> getInspectProtocols() {
            return inspectProtocols;
        }

        public void setInspectProtocols(List<String> inspectProtocols) {
            this.inspectProtocols = inspectProtocols;
        }

        public List<String> getAdminProtocols() {
            return adminProtocols;
        }

        public void setAdminProtocols(List<String> adminProtocols) {
            this.adminProtocols = adminProtocols;
        }

        public List<String> getPlatformFilterProtocols() {
            return platformFilterProtocols;
        }

        public void setPlatformFilterProtocols(List<String> platformFilterProtocols) {
            this.platformFilterProtocols = platformFilterProtocols;
        }
    }
}

package com.eventore.controlplane;

import java.util.ArrayList;
import java.util.Collections;
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
        return Collections.unmodifiableList(providers);
    }

    public void setProviders(List<StreamProviderDescriptor> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : new ArrayList<>();
    }

    public List<String> getActiveProtocols() {
        return Collections.unmodifiableList(activeProtocols);
    }

    public void setActiveProtocols(List<String> activeProtocols) {
        this.activeProtocols = activeProtocols != null ? new ArrayList<>(activeProtocols) : new ArrayList<>();
    }

    public List<String> getOpenApiStreams() {
        return Collections.unmodifiableList(openApiStreams);
    }

    public void setOpenApiStreams(List<String> openApiStreams) {
        this.openApiStreams = openApiStreams != null ? new ArrayList<>(openApiStreams) : new ArrayList<>();
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
            return Collections.unmodifiableList(connectionProtocols);
        }

        public void setConnectionProtocols(List<String> connectionProtocols) {
            this.connectionProtocols =
                    connectionProtocols != null ? new ArrayList<>(connectionProtocols) : new ArrayList<>();
        }

        public List<String> getInspectProtocols() {
            return Collections.unmodifiableList(inspectProtocols);
        }

        public void setInspectProtocols(List<String> inspectProtocols) {
            this.inspectProtocols = inspectProtocols != null ? new ArrayList<>(inspectProtocols) : new ArrayList<>();
        }

        public List<String> getAdminProtocols() {
            return Collections.unmodifiableList(adminProtocols);
        }

        public void setAdminProtocols(List<String> adminProtocols) {
            this.adminProtocols = adminProtocols != null ? new ArrayList<>(adminProtocols) : new ArrayList<>();
        }

        public List<String> getPlatformFilterProtocols() {
            return Collections.unmodifiableList(platformFilterProtocols);
        }

        public void setPlatformFilterProtocols(List<String> platformFilterProtocols) {
            this.platformFilterProtocols = platformFilterProtocols != null
                    ? new ArrayList<>(platformFilterProtocols)
                    : new ArrayList<>();
        }
    }
}

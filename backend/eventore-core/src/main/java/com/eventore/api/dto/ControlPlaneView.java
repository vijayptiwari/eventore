package com.eventore.api.dto;

import com.eventore.controlplane.ControlPlaneSnapshot;

/** Embedded control-plane summary returned on GET /config for UI cascade. */
public class ControlPlaneView {

    private long revision;
    private ControlPlaneSnapshot.UiCascade uiCascade;
    private java.util.List<String> openApiStreams;

    public static ControlPlaneView from(ControlPlaneSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("control plane snapshot is required");
        }
        ControlPlaneView view = new ControlPlaneView();
        view.revision = snapshot.getRevision();
        view.uiCascade = snapshot.getUiCascade();
        view.openApiStreams = java.util.List.copyOf(snapshot.getOpenApiStreams());
        return view;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public ControlPlaneSnapshot.UiCascade getUiCascade() {
        return uiCascade;
    }

    public void setUiCascade(ControlPlaneSnapshot.UiCascade uiCascade) {
        this.uiCascade = uiCascade;
    }

    public java.util.List<String> getOpenApiStreams() {
        return openApiStreams != null ? java.util.Collections.unmodifiableList(openApiStreams) : java.util.List.of();
    }

    public void setOpenApiStreams(java.util.List<String> openApiStreams) {
        this.openApiStreams = openApiStreams != null ? new java.util.ArrayList<>(openApiStreams) : null;
    }
}

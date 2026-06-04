package com.eventore.api.dto;

import com.eventore.controlplane.ControlPlaneSnapshot;

/** Embedded control-plane summary returned on GET /config for UI cascade. */
public class ControlPlaneView {

    private long revision;
    private ControlPlaneSnapshot.UiCascade uiCascade;
    private java.util.List<String> openApiStreams;

    public static ControlPlaneView from(ControlPlaneSnapshot snapshot) {
        ControlPlaneView view = new ControlPlaneView();
        view.revision = snapshot.getRevision();
        view.uiCascade = snapshot.getUiCascade();
        view.openApiStreams = snapshot.getOpenApiStreams();
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
        return openApiStreams;
    }

    public void setOpenApiStreams(java.util.List<String> openApiStreams) {
        this.openApiStreams = openApiStreams;
    }
}

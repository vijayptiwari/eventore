package com.eventore.controlplane;

import java.util.ArrayList;
import java.util.List;

/** Declarative capabilities exposed to the UI and API catalog (control plane only). */
public class ProviderCapabilities {

    private boolean messaging;
    private boolean inspect;
    private boolean admin;
    private boolean liveView;
    private List<String> dataPlaneApiPrefixes = new ArrayList<>();

    public boolean isMessaging() {
        return messaging;
    }

    public void setMessaging(boolean messaging) {
        this.messaging = messaging;
    }

    public boolean isInspect() {
        return inspect;
    }

    public void setInspect(boolean inspect) {
        this.inspect = inspect;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isLiveView() {
        return liveView;
    }

    public void setLiveView(boolean liveView) {
        this.liveView = liveView;
    }

    public List<String> getDataPlaneApiPrefixes() {
        return dataPlaneApiPrefixes;
    }

    public void setDataPlaneApiPrefixes(List<String> dataPlaneApiPrefixes) {
        this.dataPlaneApiPrefixes = dataPlaneApiPrefixes != null ? dataPlaneApiPrefixes : new ArrayList<>();
    }
}

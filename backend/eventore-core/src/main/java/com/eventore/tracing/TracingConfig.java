package com.eventore.tracing;

import java.io.Serializable;

/**
 * Tracing configuration defining APM viewer integration and header propagation rules.
 */
public class TracingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_JAEGER_URL = "http://localhost:16686/trace/{traceId}";
    public static final String DEFAULT_ZIPKIN_URL = "http://localhost:9411/zipkin/traces/{traceId}";
    public static final String DEFAULT_DATADOG_URL = "https://app.datadoghq.com/apm/trace/{traceId}";

    private boolean enabled = true;
    private String viewerType = "JAEGER"; // JAEGER, ZIPKIN, DATADOG, CUSTOM, NONE
    private String urlTemplate = DEFAULT_JAEGER_URL;
    private boolean injectOnPublish = true;

    public TracingConfig() {
    }

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
        this.viewerType = viewerType != null ? viewerType.toUpperCase() : "JAEGER";
        // Default URL template according to viewer type if not customized
        if (urlTemplate == null || urlTemplate.isEmpty() ||
                urlTemplate.equals(DEFAULT_JAEGER_URL) ||
                urlTemplate.equals(DEFAULT_ZIPKIN_URL) ||
                urlTemplate.equals(DEFAULT_DATADOG_URL)) {
            switch (this.viewerType) {
                case "ZIPKIN":
                    this.urlTemplate = DEFAULT_ZIPKIN_URL;
                    break;
                case "DATADOG":
                    this.urlTemplate = DEFAULT_DATADOG_URL;
                    break;
                case "CUSTOM":
                    break;
                case "NONE":
                    this.urlTemplate = null;
                    break;
                case "JAEGER":
                default:
                    this.urlTemplate = DEFAULT_JAEGER_URL;
                    break;
            }
        }
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

    /**
     * Resolves the target external trace viewer URL for a given trace ID.
     */
    public String resolveViewerUrl(String traceId) {
        if (!enabled || "NONE".equalsIgnoreCase(viewerType) || traceId == null || traceId.trim().isEmpty()) {
            return null;
        }
        String template = urlTemplate;
        if (template == null || template.trim().isEmpty()) {
            if ("ZIPKIN".equalsIgnoreCase(viewerType)) {
                template = DEFAULT_ZIPKIN_URL;
            } else if ("DATADOG".equalsIgnoreCase(viewerType)) {
                template = DEFAULT_DATADOG_URL;
            } else {
                template = DEFAULT_JAEGER_URL;
            }
        }
        return template.replace("{traceId}", traceId.trim());
    }
}

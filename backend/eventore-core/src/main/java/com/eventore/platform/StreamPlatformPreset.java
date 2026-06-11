package com.eventore.platform;

import com.eventore.domain.CloudProvider;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.StreamPlatform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StreamPlatformPreset {

    private StreamPlatform platform;
    private String label;
    private String description;
    private CloudProvider cloudProvider;
    private ProtocolType protocol;
    private String brokerUrlHint;
    private Map<String, String> defaultProperties = new LinkedHashMap<>();
    private List<String> credentialFields = new ArrayList<>();
    private List<String> features = new ArrayList<>();

    public StreamPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(StreamPlatform platform) {
        this.platform = platform;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CloudProvider getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(CloudProvider cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public String getBrokerUrlHint() {
        return brokerUrlHint;
    }

    public void setBrokerUrlHint(String brokerUrlHint) {
        this.brokerUrlHint = brokerUrlHint;
    }

    public Map<String, String> getDefaultProperties() {
        return Collections.unmodifiableMap(defaultProperties);
    }

    public void setDefaultProperties(Map<String, String> defaultProperties) {
        this.defaultProperties = defaultProperties != null ? new LinkedHashMap<>(defaultProperties) : new LinkedHashMap<>();
    }

    public List<String> getCredentialFields() {
        return Collections.unmodifiableList(credentialFields);
    }

    public void setCredentialFields(List<String> credentialFields) {
        this.credentialFields = credentialFields != null ? new ArrayList<>(credentialFields) : new ArrayList<>();
    }

    public List<String> getFeatures() {
        return Collections.unmodifiableList(features);
    }

    public void setFeatures(List<String> features) {
        this.features = features != null ? new ArrayList<>(features) : new ArrayList<>();
    }
}

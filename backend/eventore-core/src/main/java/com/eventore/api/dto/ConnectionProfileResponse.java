package com.eventore.api.dto;

import com.eventore.domain.CloudProvider;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.StreamPlatform;
import java.util.Map;

public class ConnectionProfileResponse {

    private String id;
    private String name;
    private ProtocolType protocol;
    private CloudProvider cloudProvider;
    private StreamPlatform streamPlatform;
    private String brokerUrl;
    private Map<String, String> properties;
    private boolean hasCredentials;

    public static ConnectionProfileResponse from(ConnectionProfile profile) {
        ConnectionProfileResponse r = new ConnectionProfileResponse();
        r.id = profile.getId();
        r.name = profile.getName();
        r.protocol = profile.getProtocol();
        r.cloudProvider = profile.getCloudProvider();
        r.streamPlatform = profile.getStreamPlatform();
        r.brokerUrl = profile.getBrokerUrl();
        r.properties = profile.getProperties();
        r.hasCredentials =
                profile.getCredentials() != null && !profile.getCredentials().isEmpty();
        return r;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public CloudProvider getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(CloudProvider cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public StreamPlatform getStreamPlatform() {
        return streamPlatform;
    }

    public void setStreamPlatform(StreamPlatform streamPlatform) {
        this.streamPlatform = streamPlatform;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public boolean isHasCredentials() {
        return hasCredentials;
    }

    public void setHasCredentials(boolean hasCredentials) {
        this.hasCredentials = hasCredentials;
    }
}

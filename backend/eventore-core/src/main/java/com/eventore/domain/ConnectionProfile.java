package com.eventore.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConnectionProfile {

    private String id = UUID.randomUUID().toString();
    private String name;
    private ProtocolType protocol;
    private CloudProvider cloudProvider = CloudProvider.ON_PREM;
    private StreamPlatform streamPlatform = StreamPlatform.GENERIC;
    private String brokerUrl;
    private Map<String, String> properties = new HashMap<>();
    private Map<String, String> credentials = new HashMap<>();

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
        this.cloudProvider = cloudProvider != null ? cloudProvider : CloudProvider.ON_PREM;
    }

    public StreamPlatform getStreamPlatform() {
        return streamPlatform;
    }

    public void setStreamPlatform(StreamPlatform streamPlatform) {
        this.streamPlatform = streamPlatform != null ? streamPlatform : StreamPlatform.GENERIC;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    /** Returns an unmodifiable view; use {@link #setProperties(Map)} to replace entries. */
    public Map<String, String> getProperties() {
        return properties == null ? Map.of() : Collections.unmodifiableMap(properties);
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    /** Returns an unmodifiable view; use {@link #setCredentials(Map)} to replace entries. */
    public Map<String, String> getCredentials() {
        return credentials == null ? Map.of() : Collections.unmodifiableMap(credentials);
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials != null ? new HashMap<>(credentials) : new HashMap<>();
    }

    public String property(String key) {
        return properties.get(key);
    }

    public String propertyOrDefault(String key, String defaultValue) {
        String value = properties.get(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    /**
     * Returns the credential value, resolving {@code env:} and {@code file:}
     * secret references so profiles can avoid storing plaintext secrets.
     */
    public String credential(String key) {
        return SecretRefs.resolve(credentials.get(key));
    }
}

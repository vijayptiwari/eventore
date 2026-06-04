package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import java.time.Instant;

/**
 * Control-plane record for a stream provider. Contains no broker clients or data-plane handles.
 */
public class StreamProviderDescriptor {

    private ProtocolType protocol;
    private String moduleId;
    private String connectorClass;
    private ProviderLifecycleState state;
    private ProviderCapabilities capabilities;
    private String openApiStreamId;
    private long registrationEpoch;
    private Instant registeredAt;
    private Instant deregisteredAt;

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getConnectorClass() {
        return connectorClass;
    }

    public void setConnectorClass(String connectorClass) {
        this.connectorClass = connectorClass;
    }

    public ProviderLifecycleState getState() {
        return state;
    }

    public void setState(ProviderLifecycleState state) {
        this.state = state;
    }

    public ProviderCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(ProviderCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public String getOpenApiStreamId() {
        return openApiStreamId;
    }

    public void setOpenApiStreamId(String openApiStreamId) {
        this.openApiStreamId = openApiStreamId;
    }

    public long getRegistrationEpoch() {
        return registrationEpoch;
    }

    public void setRegistrationEpoch(long registrationEpoch) {
        this.registrationEpoch = registrationEpoch;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Instant getDeregisteredAt() {
        return deregisteredAt;
    }

    public void setDeregisteredAt(Instant deregisteredAt) {
        this.deregisteredAt = deregisteredAt;
    }

    public boolean isActive() {
        return state == ProviderLifecycleState.REGISTERED;
    }
}

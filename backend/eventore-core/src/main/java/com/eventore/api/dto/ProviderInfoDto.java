package com.eventore.api.dto;

import com.eventore.domain.ProtocolType;

public class ProviderInfoDto {

    private ProtocolType protocol;
    private String moduleId;
    private boolean hasInspector;
    private String connectorClass;

    public ProviderInfoDto() {}

    public ProviderInfoDto(ProtocolType protocol, String moduleId, boolean hasInspector, String connectorClass) {
        this.protocol = protocol;
        this.moduleId = moduleId;
        this.hasInspector = hasInspector;
        this.connectorClass = connectorClass;
    }

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

    public boolean isHasInspector() {
        return hasInspector;
    }

    public void setHasInspector(boolean hasInspector) {
        this.hasInspector = hasInspector;
    }

    public String getConnectorClass() {
        return connectorClass;
    }

    public void setConnectorClass(String connectorClass) {
        this.connectorClass = connectorClass;
    }
}

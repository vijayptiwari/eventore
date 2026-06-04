package com.eventore.connector;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.dataplane.DataPlaneRegistry;
import com.eventore.domain.ProtocolType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Data-plane facade for messaging connectors (broker I/O). */
@Component
public class ConnectorRegistry {

    private final DataPlaneRegistry dataPlane;

    public ConnectorRegistry(DataPlaneRegistry dataPlane) {
        this.dataPlane = dataPlane;
    }

    public MessagingConnector get(ProtocolType protocol) {
        return dataPlane.connector(protocol);
    }

    public Map<ProtocolType, MessagingConnector> all() {
        Map<ProtocolType, MessagingConnector> map = new EnumMap<>(ProtocolType.class);
        dataPlane.attachedProviders().forEach((type, provider) -> map.put(type, provider.connector()));
        return Map.copyOf(map);
    }
}

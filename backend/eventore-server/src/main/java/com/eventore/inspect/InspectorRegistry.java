package com.eventore.inspect;

import com.eventore.dataplane.DataPlaneRegistry;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import org.springframework.stereotype.Component;

/** Data-plane facade for stream inspectors (read-only broker metadata and search). */
@Component
public class InspectorRegistry {

    private final DataPlaneRegistry dataPlane;

    public InspectorRegistry(DataPlaneRegistry dataPlane) {
        this.dataPlane = dataPlane;
    }

    public MessagingInspector get(ProtocolType protocol) {
        return dataPlane.inspector(protocol);
    }
}

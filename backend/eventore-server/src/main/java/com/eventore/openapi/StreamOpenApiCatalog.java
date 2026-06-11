package com.eventore.openapi;

import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.controlplane.StreamProviderDescriptor;
import com.eventore.domain.ProtocolType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StreamOpenApiCatalog {

    private static final Map<String, StreamSpec> ALL = new LinkedHashMap<>();

    static {
        ALL.put(
                "control",
                new StreamSpec(
                        "control",
                        "Eventore Control Plane API",
                        null,
                        "/openapi/streams/control-api.yaml",
                        true,
                        "control"));
        ALL.put(
                "core",
                new StreamSpec("core", "Eventore Core API", null, "/openapi/streams/core-api.yaml", true, "data"));
        ALL.put(
                "inspect",
                new StreamSpec(
                        "inspect",
                        "Eventore Inspect API",
                        null,
                        "/openapi/streams/inspect-api.yaml",
                        true,
                        "data"));
        ALL.put(
                "diagnostics",
                new StreamSpec(
                        "diagnostics",
                        "Eventore Diagnostics API",
                        null,
                        "/openapi/streams/diagnostics-api.yaml",
                        true,
                        "operator"));
        ALL.put(
                "kafka",
                new StreamSpec(
                        "kafka",
                        "Eventore Kafka Stream API",
                        ProtocolType.KAFKA,
                        "/openapi/streams/kafka-api.yaml",
                        false,
                        "data"));
        ALL.put(
                "kinesis",
                new StreamSpec(
                        "kinesis",
                        "Eventore Kinesis Stream API",
                        ProtocolType.KINESIS,
                        "/openapi/streams/kinesis-api.yaml",
                        false,
                        "data"));
    }

    private final ControlPlaneRegistry controlPlane;

    public StreamOpenApiCatalog(ControlPlaneRegistry controlPlane) {
        this.controlPlane = controlPlane;
    }

    public List<StreamSpec> listForDeployment() {
        List<StreamSpec> result = new ArrayList<>();
        for (StreamSpec spec : ALL.values()) {
            if (spec.alwaysOn()) {
                result.add(spec);
                continue;
            }
            if (spec.protocol() != null && controlPlane.isRegistered(spec.protocol())) {
                result.add(spec);
            }
        }
        return result;
    }

    public StreamSpec get(String streamId) {
        StreamSpec spec = ALL.get(streamId);
        if (spec == null) {
            return null;
        }
        if (spec.alwaysOn()) {
            return spec;
        }
        if (spec.protocol() != null && controlPlane.isRegistered(spec.protocol())) {
            return spec;
        }
        return null;
    }

    public record StreamSpec(
            String streamId,
            String title,
            ProtocolType protocol,
            String specUrl,
            boolean alwaysOn,
            String plane) {}
}

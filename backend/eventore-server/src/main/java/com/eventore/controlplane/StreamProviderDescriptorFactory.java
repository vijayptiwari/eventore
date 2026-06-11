package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import com.eventore.provider.StreamProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StreamProviderDescriptorFactory {

    private StreamProviderDescriptorFactory() {}

    public static StreamProviderDescriptor fromProvider(StreamProvider provider) {
        StreamProviderDescriptor descriptor = new StreamProviderDescriptor();
        descriptor.setProtocol(provider.protocol());
        descriptor.setModuleId(provider.moduleId());
        descriptor.setConnectorClass(provider.connector().getClass().getName());
        descriptor.setOpenApiStreamId(openApiStreamId(provider.protocol()));
        descriptor.setCapabilities(capabilitiesFor(provider));
        return descriptor;
    }

    private static ProviderCapabilities capabilitiesFor(StreamProvider provider) {
        ProviderCapabilities caps = new ProviderCapabilities();
        caps.setMessaging(true);
        caps.setInspect(provider.inspector().isPresent());
        caps.setAdmin(
                provider.protocol() == ProtocolType.KAFKA
                        || provider.protocol() == ProtocolType.KINESIS);
        caps.setLiveView(true);
        caps.setDataPlaneApiPrefixes(dataPlanePrefixes(provider.protocol()));
        return caps;
    }

    private static List<String> dataPlanePrefixes(ProtocolType protocol) {
        // LinkedHashSet keeps insertion order while preventing duplicate prefixes.
        Set<String> prefixes = new LinkedHashSet<>();
        prefixes.add("/api/v1/connections/{connectionId}/destinations");
        prefixes.add("/api/v1/connections/{connectionId}/publish");
        prefixes.add("/api/v1/connections/{connectionId}/subscribe");
        prefixes.add("/api/v1/connections/{connectionId}/inspect");
        switch (protocol) {
            case KAFKA -> prefixes.add("/api/v1/connections/{connectionId}/kafka");
            case KINESIS -> prefixes.add("/api/v1/connections/{connectionId}/kinesis");
            default -> {}
        }
        return new ArrayList<>(prefixes);
    }

    /**
     * OpenAPI stream bundle id for provider-specific admin routes, or {@code null} when the
     * protocol is exposed only through the shared connections API (no dedicated stream tag).
     */
    private static String openApiStreamId(ProtocolType protocol) {
        return switch (protocol) {
            case KAFKA -> "kafka";
            case KINESIS -> "kinesis";
            case MQTT, JMS, PULSAR, RABBITMQ, GCP_PUBSUB, AZURE_SERVICE_BUS -> null;
        };
    }
}

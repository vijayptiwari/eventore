package com.eventore.controlplane;

import com.eventore.domain.ProtocolType;
import com.eventore.provider.StreamProvider;
import java.util.ArrayList;
import java.util.List;

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
        caps.setAdmin(provider.protocol() == ProtocolType.KAFKA);
        caps.setLiveView(true);
        caps.setDataPlaneApiPrefixes(dataPlanePrefixes(provider.protocol()));
        return caps;
    }

    private static List<String> dataPlanePrefixes(ProtocolType protocol) {
        List<String> prefixes = new ArrayList<>();
        prefixes.add("/api/v1/connections/{connectionId}/destinations");
        prefixes.add("/api/v1/connections/{connectionId}/publish");
        prefixes.add("/api/v1/connections/{connectionId}/subscribe");
        if (protocol != ProtocolType.KAFKA) {
            prefixes.add("/api/v1/connections/{connectionId}/inspect");
        }
        return switch (protocol) {
            case KAFKA -> {
                List<String> kafka = new ArrayList<>(prefixes);
                kafka.add("/api/v1/connections/{connectionId}/inspect");
                kafka.add("/api/v1/connections/{connectionId}/kafka");
                yield kafka;
            }
            case KINESIS -> {
                List<String> kinesis = new ArrayList<>(prefixes);
                kinesis.add("/api/v1/connections/{connectionId}/inspect");
                kinesis.add("/api/v1/connections/{connectionId}/kinesis");
                yield kinesis;
            }
            default -> {
                prefixes.add("/api/v1/connections/{connectionId}/inspect");
                yield prefixes;
            }
        };
    }

    private static String openApiStreamId(ProtocolType protocol) {
        return switch (protocol) {
            case KAFKA -> "kafka";
            case KINESIS -> "kinesis";
            case MQTT, JMS, PULSAR, RABBITMQ, GCP_PUBSUB, AZURE_SERVICE_BUS -> null;
        };
    }
}

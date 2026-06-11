package com.eventore.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eventore.connector.spi.MessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StreamProviderDescriptorFactoryTest {

    private static final String INSPECT_PREFIX = "/api/v1/connections/{connectionId}/inspect";

    @Test
    void dataPlanePrefixesIncludeInspectExactlyOnceForKafka() {
        StreamProviderDescriptor descriptor = StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KAFKA));

        assertThat(descriptor.getCapabilities().getDataPlaneApiPrefixes())
                .contains(INSPECT_PREFIX)
                .doesNotHaveDuplicates();
    }

    @Test
    void dataPlanePrefixesIncludeInspectExactlyOnceForKinesis() {
        StreamProviderDescriptor descriptor =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KINESIS));

        assertThat(descriptor.getCapabilities().getDataPlaneApiPrefixes())
                .contains(INSPECT_PREFIX)
                .doesNotHaveDuplicates();
    }

    @Test
    void openApiStreamIdIsNullForSharedConnectionsOnlyProtocols() {
        StreamProviderDescriptor descriptor =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.MQTT));

        assertThat(descriptor.getOpenApiStreamId()).isNull();
    }

    @Test
    void ac1_andAc4_kinesisAdminTrueMqttAdminFalse() {
        StreamProviderDescriptor kinesis =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KINESIS));
        StreamProviderDescriptor mqtt =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.MQTT));

        assertThat(kinesis.getCapabilities().isAdmin()).isTrue();
        assertThat(mqtt.getCapabilities().isAdmin()).isFalse();
    }

    @Test
    void ac5_kafkaAdminCapabilityUnchanged() {
        StreamProviderDescriptor kafka =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KAFKA));

        assertThat(kafka.getCapabilities().isAdmin()).isTrue();
    }

    @Test
    void ac3_dataPlanePrefixesIncludeKinesisAdminPath() {
        StreamProviderDescriptor kinesis =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KINESIS));

        assertThat(kinesis.getCapabilities().getDataPlaneApiPrefixes())
                .contains("/api/v1/connections/{connectionId}/kinesis");
    }

    @Test
    void openApiStreamIdIsSetForDedicatedAdminStreams() {
        StreamProviderDescriptor kafka =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KAFKA));
        StreamProviderDescriptor kinesis =
                StreamProviderDescriptorFactory.fromProvider(mockProvider(ProtocolType.KINESIS));

        assertThat(kafka.getOpenApiStreamId()).isEqualTo("kafka");
        assertThat(kinesis.getOpenApiStreamId()).isEqualTo("kinesis");
    }

    private static StreamProvider mockProvider(ProtocolType protocol) {
        StreamProvider provider = mock(StreamProvider.class);
        when(provider.protocol()).thenReturn(protocol);
        when(provider.moduleId()).thenReturn(protocol.name().toLowerCase());
        when(provider.connector()).thenReturn(mock(MessagingConnector.class));
        when(provider.inspector()).thenReturn(Optional.of(mock(MessagingInspector.class)));
        return provider;
    }
}

package com.eventore.dataplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eventore.controlplane.ControlPlaneRegistry;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataPlaneRegistryTest {

    @Mock
    private ControlPlaneRegistry controlPlane;

    private DataPlaneRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DataPlaneRegistry(controlPlane);
    }

    @Test
    void canRouteRequiresControlPlaneRegistrationAndAttachedProvider() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(true);
        registry.attach(ProtocolType.KAFKA, mockProvider(ProtocolType.KAFKA));

        assertThat(registry.canRoute(ProtocolType.KAFKA)).isTrue();
    }

    @Test
    void canRouteIsFalseWhenNotRegistered() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(false);

        assertThat(registry.canRoute(ProtocolType.KAFKA)).isFalse();
    }

    @Test
    void canRouteIsFalseWhenRegisteredButNotAttached() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(true);

        assertThat(registry.canRoute(ProtocolType.KAFKA)).isFalse();
    }

    @Test
    void requireProviderFailsWhenNotRegisteredInControlPlane() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(false);

        assertThatThrownBy(() -> registry.requireProvider(ProtocolType.KAFKA))
                .isInstanceOf(DataPlaneException.class)
                .hasMessageContaining("not registered in the control plane");
    }

    @Test
    void requireProviderFailsWhenRegisteredButNotAttached() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(true);

        assertThatThrownBy(() -> registry.requireProvider(ProtocolType.KAFKA))
                .isInstanceOf(DataPlaneException.class)
                .hasMessageContaining("No data-plane provider implementation");
    }

    @Test
    void inspectorOptionalIsEmptyWhenNotRegistered() {
        when(controlPlane.isRegistered(ProtocolType.MQTT)).thenReturn(false);

        assertThat(registry.inspectorOptional(ProtocolType.MQTT)).isEmpty();
    }

    @Test
    void inspectorOptionalIsEmptyWhenRegisteredButNotAttached() {
        when(controlPlane.isRegistered(ProtocolType.MQTT)).thenReturn(true);

        assertThat(registry.inspectorOptional(ProtocolType.MQTT)).isEmpty();
    }

    @Test
    void inspectorOptionalReturnsInspectorWhenPresent() {
        when(controlPlane.isRegistered(ProtocolType.KAFKA)).thenReturn(true);
        MessagingInspector inspector = mock(MessagingInspector.class);
        StreamProvider provider = mockProvider(ProtocolType.KAFKA);
        when(provider.inspector()).thenReturn(Optional.of(inspector));
        registry.attach(ProtocolType.KAFKA, provider);

        assertThat(registry.inspectorOptional(ProtocolType.KAFKA)).contains(inspector);
    }

    @Test
    void inspectorOptionalIsEmptyWhenProviderHasNoInspector() {
        when(controlPlane.isRegistered(ProtocolType.MQTT)).thenReturn(true);
        StreamProvider provider = mockProvider(ProtocolType.MQTT);
        when(provider.inspector()).thenReturn(Optional.empty());
        registry.attach(ProtocolType.MQTT, provider);

        assertThat(registry.inspectorOptional(ProtocolType.MQTT)).isEmpty();
    }

    @Test
    void attachRejectsNullProtocol() {
        StreamProvider provider = mock(StreamProvider.class);
        assertThatThrownBy(() -> registry.attach(null, provider))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void attachRejectsNullProvider() {
        assertThatThrownBy(() -> registry.attach(ProtocolType.KAFKA, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stream provider");
    }

    private static StreamProvider mockProvider(ProtocolType protocol) {
        return mock(StreamProvider.class);
    }
}

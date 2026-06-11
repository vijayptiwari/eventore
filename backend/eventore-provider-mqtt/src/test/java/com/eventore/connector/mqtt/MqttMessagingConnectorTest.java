package com.eventore.connector.mqtt;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttMessagingConnectorTest {

    private MqttMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new MqttMessagingConnector();
    }

    @Test
    void protocolIsMqtt() {
        assertEquals(ProtocolType.MQTT, connector.protocol());
    }

    @Test
    void listDestinationsReturnsTopicFilterFromProfile() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.MQTT, "localhost:1883", Map.of("topicFilter", "sensors/#"), null);

        List<TopicRef> destinations = connector.listDestinations(profile);

        assertEquals(1, destinations.size());
        assertEquals("sensors/#", destinations.get(0).getName());
        assertEquals("topic-filter", destinations.get(0).getType());
        assertEquals(ProtocolType.MQTT, destinations.get(0).getProtocol());
    }

    @Test
    void listDestinationsDefaultsTopicFilterToHash() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883", null, null);

        assertEquals("#", connector.listDestinations(profile).get(0).getName());
    }

    @Test
    void closeWithNoActiveClientsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-mqtt"));
    }

    @Test
    void subscribeFailureIsWrappedAsIllegalStateAndDoesNotLeakClient() {
        // Port 1 is reserved; the connection is refused locally without any broker.
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "127.0.0.1:1");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("MQTT subscribe failed"));
        // The failed client must not be registered for later cleanup.
        assertDoesNotThrow(() -> connector.close(profile.getId()));
    }

    @Test
    void subscribeWithNullOptionsUsesDefaultQosWithoutNpe() {
        // Port 1 is reserved; connection fails after options are read — proves null-safe options path.
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "127.0.0.1:1");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");
        request.setOptions(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("MQTT subscribe failed"));
    }

    @Test
    void subscribeRejectsInvalidQos() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");
        request.setOptions(Map.of("qos", "not-a-number"));

        try (MockedConstruction<MqttClient> clients = mockConstruction(MqttClient.class)) {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("subscribe qos"));
        }
    }

    @Test
    void publishRejectsInvalidBase64Payload() {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883");
        PublishRequest request = new PublishRequest();
        request.setDestination("sensors/temp");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");

        try (MockedConstruction<MqttClient> clients = mockConstruction(MqttClient.class)) {
            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
            assertTrue(ex.getMessage().contains("MQTT publish failed"));
        }
    }

    @Test
    void closeRemovesRegisteredSubscriptionClient() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");

        try (MockedConstruction<MqttClient> clients = mockConstruction(MqttClient.class)) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            subscription.close();
            verify(clients.constructed().get(0)).unsubscribe(any(String[].class));
            verify(clients.constructed().get(0)).disconnect();
            verify(clients.constructed().get(0)).close();
            assertDoesNotThrow(() -> connector.close(profile.getId()));
        }
    }

    @Test
    void subscribeSuccessPathConnectsAndSubscribesWithConfiguredQos() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");
        request.setOptions(Map.of("qos", "2"));

        try (MockedConstruction<MqttClient> clients = mockConstruction(
                MqttClient.class, (mock, ctx) -> when(mock.isConnected()).thenReturn(false))) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            MqttClient client = clients.constructed().get(0);
            verify(client).connect(any(MqttConnectOptions.class));
            ArgumentCaptor<String[]> topicsCaptor = ArgumentCaptor.forClass(String[].class);
            ArgumentCaptor<int[]> qosCaptor = ArgumentCaptor.forClass(int[].class);
            verify(client).subscribe(topicsCaptor.capture(), qosCaptor.capture());
            assertArrayEquals(new String[] {"sensors/temp"}, topicsCaptor.getValue());
            assertArrayEquals(new int[] {2}, qosCaptor.getValue());
            subscription.close();
        }
    }

    @Test
    void subscribeDeliversIncomingMessageToHandler() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.MQTT, "localhost:1883");
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("sensors/temp");
        AtomicReference<UnifiedMessage> received = new AtomicReference<>();
        ArgumentCaptor<MqttCallback> callbackCaptor = ArgumentCaptor.forClass(MqttCallback.class);

        try (MockedConstruction<MqttClient> clients = mockConstruction(MqttClient.class)) {
            connector.subscribe(profile, request, received::set);
            verify(clients.constructed().get(0)).setCallback(callbackCaptor.capture());

            MqttMessage message = new MqttMessage("payload".getBytes());
            message.setQos(1);
            callbackCaptor.getValue().messageArrived("sensors/temp", message);

            assertEquals("payload", received.get().getPayload());
            assertEquals("sensors/temp", received.get().getDestination());
            assertEquals(ProtocolType.MQTT, received.get().getProtocol());
            assertEquals("1", received.get().getHeaders().get("qos"));
        }
    }
}

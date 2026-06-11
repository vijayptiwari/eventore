package com.eventore.connector.azure;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureServiceBusMessagingConnectorTest {

    private static final String TEST_CONNECTION_STRING =
            "Endpoint=sb://example.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=dGVzdA==";

    private AzureServiceBusMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new AzureServiceBusMessagingConnector();
    }

    @Test
    void protocolIsAzureServiceBus() {
        assertEquals(ProtocolType.AZURE_SERVICE_BUS, connector.protocol());
    }

    @Test
    void closeWithNoActiveProcessorsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-azure"));
    }

    @Test
    void validateFailsWithoutConnectionString() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        assertThrows(IllegalArgumentException.class, () -> connector.validate(profile));
    }

    @Test
    void subscribeFailsWithoutConnectionString() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("connectionString"));
    }

    @Test
    void subscribeSetupFailureDoesNotRegisterProcessorWithoutConnectionString() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        assertThrows(IllegalArgumentException.class, () -> connector.subscribe(profile, request, msg -> {}));
        assertDoesNotThrow(() -> connector.close(profile.getId()));
    }

    @Test
    void publishFailsWithoutConnectionString() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("hello");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> connector.publish(profile, request));

        assertTrue(ex.getMessage().contains("connectionString"));
    }

    @Test
    void publishRejectsInvalidBase64Payload() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS,
                "unused",
                null,
                Map.of("connectionString", TEST_CONNECTION_STRING));
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> connector.publish(profile, request));

        assertTrue(ex.getMessage().contains("base64"));
    }

    @Test
    void subscribeProcessorStartFailureClosesProcessor() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS,
                "unused",
                null,
                Map.of("connectionString", TEST_CONNECTION_STRING));
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        ServiceBusProcessorClient processor = mock(ServiceBusProcessorClient.class);
        doThrow(new RuntimeException("start failed")).when(processor).start();
        ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorBuilder =
                mock(ServiceBusClientBuilder.ServiceBusProcessorClientBuilder.class);
        when(processorBuilder.queueName(anyString())).thenReturn(processorBuilder);
        when(processorBuilder.processMessage(any())).thenReturn(processorBuilder);
        when(processorBuilder.processError(any())).thenReturn(processorBuilder);
        when(processorBuilder.buildProcessorClient()).thenReturn(processor);

        try (MockedConstruction<ServiceBusClientBuilder> builders = mockConstruction(
                ServiceBusClientBuilder.class,
                (builder, ctx) -> {
                    when(builder.connectionString(anyString())).thenReturn(builder);
                    when(builder.processor()).thenReturn(processorBuilder);
                })) {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

            assertTrue(ex.getMessage().contains("Azure Service Bus subscribe failed"));
            assertTrue(ex.getMessage().contains("start failed"));
            verify(processor).close();
            assertDoesNotThrow(() -> connector.close(profile.getId()));
        }
    }

    @Test
    void subscribeWithNullOptionsUsesDefaultEntityTypeWithoutNpe() throws Exception {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS,
                "unused",
                null,
                Map.of("connectionString", TEST_CONNECTION_STRING));
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");
        request.setOptions(null);

        ServiceBusProcessorClient processor = mock(ServiceBusProcessorClient.class);
        ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorBuilder =
                mock(ServiceBusClientBuilder.ServiceBusProcessorClientBuilder.class);
        when(processorBuilder.queueName(anyString())).thenReturn(processorBuilder);
        when(processorBuilder.processMessage(any())).thenReturn(processorBuilder);
        when(processorBuilder.processError(any())).thenReturn(processorBuilder);
        when(processorBuilder.buildProcessorClient()).thenReturn(processor);

        try (MockedConstruction<ServiceBusClientBuilder> builders = mockConstruction(
                ServiceBusClientBuilder.class,
                (builder, ctx) -> {
                    when(builder.connectionString(anyString())).thenReturn(builder);
                    when(builder.processor()).thenReturn(processorBuilder);
                })) {
            AutoCloseable subscription = connector.subscribe(profile, request, msg -> {});
            verify(processorBuilder).queueName("orders");
            verify(processor).start();
            subscription.close();
            verify(processor).close();
        }
    }
}

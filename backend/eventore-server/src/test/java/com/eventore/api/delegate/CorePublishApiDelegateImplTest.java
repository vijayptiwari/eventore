package com.eventore.api.delegate;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorePublishApiDelegateImplTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private AuditService auditService;

    @Mock
    private MessagingConnector messagingConnector;

    private CorePublishApiDelegateImpl delegate;

    @BeforeEach
    void setUp() {
        delegate = new CorePublishApiDelegateImpl(
                connectionRegistry, connectorRegistry, policy, auditService);
    }

    @Test
    void publishAcceptsBase64PayloadSizedByDecodedBytes() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("conn-kafka")).thenReturn(java.util.Optional.of(profile));
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(messagingConnector);

        byte[] raw = new byte[100];
        String encoded = Base64.getEncoder().encodeToString(raw);
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setDestination("orders");
        publishRequest.setPayload(encoded);
        publishRequest.setContentType(PayloadCodec.BASE64_CONTENT_TYPE);

        doNothing().when(policy).require(any());
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);
        doNothing().when(policy).validatePublishSize(100);
        doNothing().when(messagingConnector).publish(eq(profile), eq(publishRequest));

        var response = delegate.publishMessage("conn-kafka", publishRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(policy).validatePublishSize(100);
        verify(messagingConnector).publish(profile, publishRequest);
    }

    @Test
    void publishRejectsBase64PayloadWhenDecodedBytesExceedLimit() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("conn-kafka")).thenReturn(java.util.Optional.of(profile));

        byte[] raw = new byte[200];
        String encoded = Base64.getEncoder().encodeToString(raw);
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setDestination("orders");
        publishRequest.setPayload(encoded);
        publishRequest.setContentType(PayloadCodec.BASE64_CONTENT_TYPE);

        doNothing().when(policy).require(any());
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE))
                .when(policy)
                .validatePublishSize(200);

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> delegate.publishMessage("conn-kafka", publishRequest));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
    }

    @Test
    void publishValidatesSchemaSuccessfullyWhenConforming() {
        var schemaClient = new com.eventore.schema.DefaultSchemaRegistryClient();
        schemaClient.registerLocalSchema(
                301,
                "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"double\"}]}",
                "AVRO");

        CorePublishApiDelegateImpl schemaDelegate = new CorePublishApiDelegateImpl(
                connectionRegistry, connectorRegistry, policy, auditService, schemaClient);

        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("conn-kafka")).thenReturn(java.util.Optional.of(profile));
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(messagingConnector);

        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setDestination("orders");
        publishRequest.setPayload("{\"id\":\"o-123\",\"amount\":59.99}");
        publishRequest.setContentType("application/json");
        publishRequest.setHeaders(java.util.Map.of("x-eventore-schema-id", "301"));

        doNothing().when(policy).require(any());
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);
        doNothing().when(policy).validatePublishSize(org.mockito.ArgumentMatchers.anyLong());
        doNothing().when(messagingConnector).publish(eq(profile), eq(publishRequest));

        var response = schemaDelegate.publishMessage("conn-kafka", publishRequest);
        assertEquals(200, response.getStatusCode().value());
        verify(messagingConnector).publish(profile, publishRequest);
    }

    @Test
    void publishRejectsInvalidPayloadWhenSchemaHeaderPresent() {
        var schemaClient = new com.eventore.schema.DefaultSchemaRegistryClient();
        schemaClient.registerLocalSchema(
                301,
                "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"double\"}]}",
                "AVRO");

        CorePublishApiDelegateImpl schemaDelegate = new CorePublishApiDelegateImpl(
                connectionRegistry, connectorRegistry, policy, auditService, schemaClient);

        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-kafka");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("conn-kafka")).thenReturn(java.util.Optional.of(profile));

        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setDestination("orders");
        publishRequest.setPayload("{\"id\":\"o-123\"}"); // missing amount
        publishRequest.setContentType("application/json");
        publishRequest.setHeaders(java.util.Map.of("x-eventore-schema-id", "301"));

        doNothing().when(policy).require(any());
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> schemaDelegate.publishMessage("conn-kafka", publishRequest));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Payload failed schema validation"));
    }
}

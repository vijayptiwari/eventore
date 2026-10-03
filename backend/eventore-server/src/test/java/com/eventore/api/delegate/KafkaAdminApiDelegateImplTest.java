package com.eventore.api.delegate;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.kafka.KafkaAdminModels;
import com.eventore.inspect.kafka.KafkaAdminService;
import com.eventore.schema.DefaultSchemaRegistryClient;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.Map;
import java.util.Optional;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaAdminApiDelegateImplTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private KafkaAdminService kafkaAdmin;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private AuditService auditService;

    private DefaultSchemaRegistryClient schemaRegistryClient;
    private KafkaAdminApiDelegateImpl delegate;

    private static final String SENSOR_SCHEMA = """
        {
          "type": "record",
          "name": "Sensor",
          "fields": [
            {"name": "sensorId", "type": "string"},
            {"name": "reading", "type": "double"}
          ]
        }
        """;

    @BeforeEach
    void setUp() {
        schemaRegistryClient = new DefaultSchemaRegistryClient();
        schemaRegistryClient.registerLocalSchema(4040, SENSOR_SCHEMA, "AVRO");
        delegate = new KafkaAdminApiDelegateImpl(
                connectionRegistry, kafkaAdmin, policy, auditService, schemaRegistryClient);
    }

    @Test
    void kafkaPublishValidatesSchemaSuccessfullyWhenConforming() throws Exception {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("kafka-conn");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("kafka-conn")).thenReturn(Optional.of(profile));

        PublishRequest request = new PublishRequest();
        request.setDestination("sensor-stream");
        request.setPayload("{\"sensorId\":\"s-1\",\"reading\":42.0}");
        request.setHeaders(Map.of("x-eventore-schema-id", "4040"));

        doNothing().when(policy).require(any());
        doNothing().when(policy).validatePublishSize(anyLong());

        KafkaAdminModels.PublishResult adminResult = new KafkaAdminModels.PublishResult();
        adminResult.setTopic("sensor-stream");
        adminResult.setPartition(0);
        adminResult.setOffset(10L);
        when(kafkaAdmin.publish(eq(profile), eq(request), anyBoolean())).thenReturn(adminResult);

        var response = delegate.kafkaPublish("kafka-conn", false, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("sensor-stream", response.getBody().getTopic());
        assertEquals(10L, response.getBody().getOffset());
    }

    @Test
    void kafkaPublishRejectsInvalidSchemaPayload() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("kafka-conn");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("kafka-conn")).thenReturn(Optional.of(profile));

        PublishRequest request = new PublishRequest();
        request.setDestination("sensor-stream");
        request.setPayload("{\"sensorId\":\"s-1\"}"); // missing required field 'reading'
        request.setHeaders(Map.of("x-eventore-schema-id", "4040"));

        doNothing().when(policy).require(any());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> delegate.kafkaPublish("kafka-conn", false, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Payload failed schema validation"));
    }
}

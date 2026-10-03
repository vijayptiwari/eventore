package com.eventore.api;

import com.eventore.api.SchemaRegistryController.RegisterSchemaPayload;
import com.eventore.config.EventoreProperties;
import com.eventore.controlplane.DefaultControlPlaneRegistry;
import com.eventore.schema.DefaultSchemaRegistryClient;
import com.eventore.schema.SchemaMetadata;
import com.eventore.security.DeploymentMode;
import com.eventore.security.DeploymentModePolicy;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRegistryControllerTest {

    private DefaultSchemaRegistryClient client;
    private DeploymentModePolicy policy;
    private SchemaRegistryController controller;

    @BeforeEach
    void setUp() {
        client = new DefaultSchemaRegistryClient();
        EventoreProperties props = new EventoreProperties();
        props.setDeploymentMode(DeploymentMode.DEV);
        policy = new DeploymentModePolicy(props, new DefaultControlPlaneRegistry());
        controller = new SchemaRegistryController(client, policy);
    }

    @Test
    void listSchemasReturnsRegisteredSchemas() {
        client.registerLocalSchema(101, "{\"type\":\"record\",\"name\":\"R\",\"fields\":[]}", "AVRO");
        Collection<SchemaMetadata> list = controller.listSchemas();
        assertEquals(1, list.size());
        assertEquals(101, list.iterator().next().schemaId());
    }

    @Test
    void registerAndGetSchema() {
        RegisterSchemaPayload payload = new RegisterSchemaPayload(
                1042,
                "user-value",
                "AVRO",
                "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}",
                null);

        ResponseEntity<SchemaMetadata> response = controller.registerSchema(payload);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1042, response.getBody().schemaId());

        ResponseEntity<SchemaMetadata> fetched = controller.getSchema(1042, null);
        assertEquals(200, fetched.getStatusCode().value());
        assertEquals(1042, fetched.getBody().schemaId());
        assertTrue(fetched.getBody().schemaContent().contains("\"name\":\"User\""));
    }

    @Test
    void getSchemaReturns404WhenNotFound() {
        ResponseEntity<SchemaMetadata> fetched = controller.getSchema(99999, null);
        assertEquals(404, fetched.getStatusCode().value());
    }

    @Test
    void registerSchemaRejectsEmptyContent() {
        RegisterSchemaPayload payload = new RegisterSchemaPayload(null, "sub", "AVRO", "", null);
        ResponseEntity<SchemaMetadata> response = controller.registerSchema(payload);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void getSchemaTemplateGeneratesJsonTemplate() {
        client.registerLocalSchema(
                2001,
                "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"orderId\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"double\"}]}",
                "AVRO");

        ResponseEntity<SchemaRegistryController.SchemaTemplateResponse> response =
                controller.getSchemaTemplate(2001, null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2001, response.getBody().schemaId());
        assertEquals("AVRO", response.getBody().schemaType());
        assertTrue(response.getBody().template().contains("\"orderId\" : \"example_orderId\""));
        assertTrue(response.getBody().template().contains("\"amount\" : 99.99"));
    }

    @Test
    void validatePayloadValidatesAgainstRegisteredSchema() {
        client.registerLocalSchema(
                2002,
                "{\"type\":\"record\",\"name\":\"Sensor\",\"fields\":[{\"name\":\"sensorId\",\"type\":\"string\"},{\"name\":\"temperature\",\"type\":\"double\"}]}",
                "AVRO");

        // Valid payload
        var validResponse = controller.validatePayload(
                2002,
                null,
                new SchemaRegistryController.ValidatePayloadRequest("{\"sensorId\":\"s-123\",\"temperature\":24.5}"));
        assertEquals(200, validResponse.getStatusCode().value());
        assertNotNull(validResponse.getBody());
        assertTrue(validResponse.getBody().valid());
        assertTrue(validResponse.getBody().errors().isEmpty());

        // Invalid payload (missing temperature)
        var invalidResponse = controller.validatePayload(
                2002,
                null,
                new SchemaRegistryController.ValidatePayloadRequest("{\"sensorId\":\"s-123\"}"));
        assertEquals(200, invalidResponse.getStatusCode().value());
        assertNotNull(invalidResponse.getBody());
        assertEquals(false, invalidResponse.getBody().valid());
        assertEquals(1, invalidResponse.getBody().errors().size());
        assertTrue(!invalidResponse.getBody().errors().get(0).isBlank());
    }
}


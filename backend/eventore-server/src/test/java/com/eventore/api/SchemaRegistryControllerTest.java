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
}

package com.eventore.schema;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaPayloadValidatorTest {

    private DefaultSchemaRegistryClient client;

    private static final String SENSOR_SCHEMA = """
        {
          "type": "record",
          "name": "SensorReading",
          "fields": [
            {"name": "sensorId", "type": "string"},
            {"name": "temp", "type": "double"}
          ]
        }
        """;

    @BeforeEach
    void setUp() {
        client = new DefaultSchemaRegistryClient();
        client.registerLocalSchema(500, SENSOR_SCHEMA, "AVRO");
    }

    @Test
    void validateReturnsSuccessWhenNoSchemaHeaderPresent() {
        SchemaValidationResult result = SchemaPayloadValidator.validate(
                client, null, "{\"any\":\"payload\"}", Map.of("other-header", "value"));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateSucceedsForConformingPayload() {
        Map<String, String> headers = Map.of("x-eventore-schema-id", "500");
        String payload = "{\"sensorId\":\"s-99\",\"temp\":21.4}";
        SchemaValidationResult result = SchemaPayloadValidator.validate(client, null, payload, headers);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validateRejectsMissingFields() {
        Map<String, String> headers = Map.of("schema-id", "500");
        String payload = "{\"sensorId\":\"s-99\"}";
        SchemaValidationResult result = SchemaPayloadValidator.validate(client, null, payload, headers);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void validateRejectsNonExistentSchema() {
        Map<String, String> headers = Map.of("x-eventore-schema-id", "9999");
        SchemaValidationResult result = SchemaPayloadValidator.validate(
                client, null, "{\"sensorId\":\"s-99\",\"temp\":21.4}", headers);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Schema with ID 9999 not found in registry");
    }

    @Test
    void validateRejectsMalformedSchemaId() {
        Map<String, String> headers = Map.of("x-eventore-schema-id", "not-a-number");
        SchemaValidationResult result = SchemaPayloadValidator.validate(
                client, null, "{}", headers);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().get(0)).contains("Invalid schema ID format");
    }
}

package com.eventore.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class AvroPayloadDecoderTest {

    private static final String USER_SCHEMA_JSON = """
            {
              "type": "record",
              "name": "User",
              "namespace": "com.eventore.example",
              "fields": [
                {"name": "id", "type": "string"},
                {"name": "username", "type": "string"},
                {"name": "age", "type": "int"}
              ]
            }
            """;

    @Test
    void wireFormatDetectionAndExtraction() {
        byte[] nonConfluent = new byte[] {0x01, 0x00, 0x00, 0x00, 0x05, 0x10};
        assertThat(AvroPayloadDecoder.isConfluentWireFormat(nonConfluent)).isFalse();

        byte[] tooShort = new byte[] {0x00, 0x00, 0x00, 0x01};
        assertThat(AvroPayloadDecoder.isConfluentWireFormat(tooShort)).isFalse();

        byte[] confluent = new byte[] {0x00, 0x00, 0x00, 0x04, 0x12, 0x20};
        assertThat(AvroPayloadDecoder.isConfluentWireFormat(confluent)).isTrue();
        assertThat(AvroPayloadDecoder.extractSchemaId(confluent)).isEqualTo(1042);
    }

    @Test
    void encodeAndDecodeConfluentAvro() throws IOException {
        Schema schema = new Schema.Parser().parse(USER_SCHEMA_JSON);
        GenericRecord record = new GenericData.Record(schema);
        record.put("id", "usr-123");
        record.put("username", "alice");
        record.put("age", 30);

        int schemaId = 42;
        byte[] wireBytes = AvroPayloadDecoder.encode(record, schemaId);

        assertThat(AvroPayloadDecoder.isConfluentWireFormat(wireBytes)).isTrue();
        assertThat(AvroPayloadDecoder.extractSchemaId(wireBytes)).isEqualTo(42);

        String json = AvroPayloadDecoder.decode(wireBytes, USER_SCHEMA_JSON);
        assertThat(json).contains("\"id\": \"usr-123\"");
        assertThat(json).contains("\"username\": \"alice\"");
        assertThat(json).contains("\"age\": 30");
    }

    @Test
    void encodeJsonToConfluentWireFormat() throws IOException {
        String jsonInput = "{\"id\":\"usr-999\",\"username\":\"bob\",\"age\":25}";
        byte[] wireBytes = AvroPayloadDecoder.encodeJson(1042, jsonInput, USER_SCHEMA_JSON);

        assertThat(AvroPayloadDecoder.isConfluentWireFormat(wireBytes)).isTrue();
        assertThat(AvroPayloadDecoder.extractSchemaId(wireBytes)).isEqualTo(1042);

        String decodedJson = AvroPayloadDecoder.decode(wireBytes, USER_SCHEMA_JSON);
        assertThat(decodedJson).contains("\"id\": \"usr-999\"");
        assertThat(decodedJson).contains("\"username\": \"bob\"");
        assertThat(decodedJson).contains("\"age\": 25");
    }

    @Test
    void extractSchemaIdThrowsOnInvalidFormat() {
        byte[] invalid = new byte[] {0x01, 0x02, 0x03};
        assertThatThrownBy(() -> AvroPayloadDecoder.extractSchemaId(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateJsonAcceptsValidPayload() {
        String validJson = "{\"id\":\"usr-1\",\"username\":\"charlie\",\"age\":28}";
        SchemaValidationResult result = AvroPayloadDecoder.validateJson(validJson, USER_SCHEMA_JSON);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validateJsonRejectsMissingFields() {
        String missingFields = "{\"id\":\"usr-1\"}";
        SchemaValidationResult result = AvroPayloadDecoder.validateJson(missingFields, USER_SCHEMA_JSON);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void validateJsonRejectsWrongTypes() {
        String wrongType = "{\"id\":\"usr-1\",\"username\":\"dave\",\"age\":\"not-a-number\"}";
        SchemaValidationResult result = AvroPayloadDecoder.validateJson(wrongType, USER_SCHEMA_JSON);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void generateTemplateJsonProducesValidPayload() {
        String template = AvroPayloadDecoder.generateTemplateJson(USER_SCHEMA_JSON);
        assertThat(template).contains("\"id\"");
        assertThat(template).contains("\"username\"");
        assertThat(template).contains("\"age\"");

        SchemaValidationResult result = AvroPayloadDecoder.validateJson(template, USER_SCHEMA_JSON);
        assertThat(result.valid()).isTrue();
    }
}

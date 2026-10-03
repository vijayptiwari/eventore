package com.eventore.schema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

/**
 * Encodes and decodes binary payloads conforming to the Confluent Schema Registry
 * Wire Format (magic byte 0x00 + 4-byte schema ID + Avro binary payload).
 */
public final class AvroPayloadDecoder {

    public static final byte MAGIC_BYTE = 0x00;
    public static final int WIRE_HEADER_SIZE = 5;

    private static final Map<String, Schema> PARSED_SCHEMAS = new ConcurrentHashMap<>();

    private AvroPayloadDecoder() {}

    /**
     * Checks if the binary payload starts with Confluent magic byte 0x00 and has at least 5 bytes.
     */
    public static boolean isConfluentWireFormat(byte[] data) {
        return data != null && data.length >= WIRE_HEADER_SIZE && data[0] == MAGIC_BYTE;
    }

    /**
     * Extracts the 4-byte big-endian Schema ID from the Confluent wire format header.
     */
    public static int extractSchemaId(byte[] data) {
        if (!isConfluentWireFormat(data)) {
            throw new IllegalArgumentException("Payload does not match Confluent Schema Registry wire format");
        }
        return ByteBuffer.wrap(data, 1, 4).getInt();
    }

    /**
     * Decodes a Confluent wire format payload using the provided schema JSON definition.
     * Skips the 5-byte wire format header and deserializes the Avro binary body into JSON.
     */
    public static String decode(byte[] data, String schemaContent) throws IOException {
        if (data == null || data.length < WIRE_HEADER_SIZE) {
            return "";
        }
        Schema schema = parseSchema(schemaContent);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                data, WIRE_HEADER_SIZE, data.length - WIRE_HEADER_SIZE, null);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        GenericRecord record = reader.read(null, decoder);
        return record != null ? record.toString() : "{}";
    }

    /**
     * Decodes raw Avro binary data (without Confluent 5-byte header) using the schema JSON.
     */
    public static String decodeRaw(byte[] data, String schemaContent) throws IOException {
        if (data == null || data.length == 0) {
            return "";
        }
        Schema schema = parseSchema(schemaContent);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        GenericRecord record = reader.read(null, decoder);
        return record != null ? record.toString() : "{}";
    }

    /**
     * Encodes an Avro GenericRecord into Confluent wire format (0x00 + 4-byte schema ID + Avro binary).
     */
    public static byte[] encode(GenericRecord record, int schemaId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC_BYTE);
        out.write(ByteBuffer.allocate(4).putInt(schemaId).array());

        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(record.getSchema());
        writer.write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    /**
     * Encodes a JSON string conforming to the Avro schema into Confluent wire format.
     */
    public static byte[] encodeJson(int schemaId, String jsonPayload, String schemaContent) throws IOException {
        Schema schema = parseSchema(schemaContent);
        org.apache.avro.io.JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(schema, jsonPayload);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        GenericRecord record = reader.read(null, jsonDecoder);
        return encode(record, schemaId);
    }

    /**
     * Validates a JSON payload against an Avro schema definition.
     */
    public static SchemaValidationResult validateJson(String jsonPayload, String schemaContent) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return SchemaValidationResult.failure("Payload cannot be empty");
        }
        if (schemaContent == null || schemaContent.isBlank()) {
            return SchemaValidationResult.failure("Schema definition cannot be empty");
        }
        try {
            Schema schema = parseSchema(schemaContent);
            org.apache.avro.io.JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(schema, jsonPayload);
            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            reader.read(null, jsonDecoder);
            return SchemaValidationResult.success();
        } catch (org.apache.avro.AvroTypeException e) {
            return SchemaValidationResult.failure("Schema type error: " + e.getMessage());
        } catch (org.apache.avro.SchemaParseException e) {
            return SchemaValidationResult.failure("Invalid schema definition: " + e.getMessage());
        } catch (Exception e) {
            return SchemaValidationResult.failure("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Generates a starter JSON template based on schema field definitions.
     */
    public static String generateTemplateJson(String schemaContent) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return "{}";
        }
        Schema schema = parseSchema(schemaContent);
        if (schema.getType() != Schema.Type.RECORD) {
            return "{}";
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
        for (Schema.Field field : schema.getFields()) {
            populateFieldDefault(node, field.name(), field.schema());
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void populateFieldDefault(
            com.fasterxml.jackson.databind.node.ObjectNode node, String fieldName, Schema fieldSchema) {
        Schema effectiveSchema = fieldSchema;
        if (effectiveSchema.getType() == Schema.Type.UNION) {
            for (Schema member : effectiveSchema.getTypes()) {
                if (member.getType() != Schema.Type.NULL) {
                    effectiveSchema = member;
                    break;
                }
            }
        }
        switch (effectiveSchema.getType()) {
            case STRING -> node.put(fieldName, "example_" + fieldName);
            case INT -> node.put(fieldName, 100);
            case LONG -> node.put(fieldName, 1000L);
            case FLOAT -> node.put(fieldName, 10.5f);
            case DOUBLE -> node.put(fieldName, 99.99);
            case BOOLEAN -> node.put(fieldName, true);
            case ARRAY -> node.putArray(fieldName);
            case MAP -> node.putObject(fieldName);
            case RECORD -> {
                com.fasterxml.jackson.databind.node.ObjectNode child = node.putObject(fieldName);
                for (Schema.Field f : effectiveSchema.getFields()) {
                    populateFieldDefault(child, f.name(), f.schema());
                }
            }
            default -> node.putNull(fieldName);
        }
    }

    /**
     * Parses or retrieves a cached Avro Schema instance from its JSON string.
     */
    public static Schema parseSchema(String schemaJson) {
        return PARSED_SCHEMAS.computeIfAbsent(schemaJson, key -> new Schema.Parser().parse(key));
    }
}

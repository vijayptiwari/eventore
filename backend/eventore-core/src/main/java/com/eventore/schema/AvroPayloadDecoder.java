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
     * Parses or retrieves a cached Avro Schema instance from its JSON string.
     */
    public static Schema parseSchema(String schemaJson) {
        return PARSED_SCHEMAS.computeIfAbsent(schemaJson, key -> new Schema.Parser().parse(key));
    }
}

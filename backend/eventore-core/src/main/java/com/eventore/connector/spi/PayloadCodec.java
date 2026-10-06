package com.eventore.connector.spi;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Encodes and decodes message payloads so binary data survives the string-based
 * transport contract. Outbound payloads with a base64 content type are decoded
 * to raw bytes before hitting the broker; inbound bytes that are not valid
 * UTF-8 are base64-encoded and flagged via content type.
 */
public final class PayloadCodec {

    public static final String BASE64_CONTENT_TYPE = "application/base64";
    public static final String TEXT_CONTENT_TYPE = "text/plain";

    private PayloadCodec() {}

    /** True when the content type indicates a base64-encoded payload. */
    public static boolean isBase64(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("base64");
    }

    /**
     * Converts an outbound payload string to raw bytes. Base64 content types are
     * decoded; everything else is treated as UTF-8 text. Null payloads become
     * empty byte arrays.
     */
    public static byte[] toBytes(String payload, String contentType) {
        if (payload == null) {
            return new byte[0];
        }
        if (isBase64(contentType)) {
            try {
                return Base64.getDecoder().decode(payload.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Payload declared as base64 but is not valid base64: " + e.getMessage(), e);
            }
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** Result of decoding inbound bytes: payload text plus the effective content type and optional schema metadata. */
    public record Decoded(
            String text,
            String contentType,
            boolean base64,
            Integer schemaId,
            String schemaType) {

        public Decoded(String text, String contentType, boolean base64) {
            this(text, contentType, base64, null, null);
        }
    }

    /**
     * Converts inbound raw bytes to a transport-safe string without external schema resolution.
     */
    public static Decoded fromBytes(byte[] data) {
        return fromBytes(data, null, null);
    }

    /**
     * Converts inbound raw bytes to a transport-safe string, automatically resolving
     * Confluent Schema Registry wire format (0x00 + 4-byte Schema ID) to decoded JSON.
     */
    public static Decoded fromBytes(
            byte[] data,
            com.eventore.schema.SchemaRegistryClient schemaRegistryClient,
            String registryUrl) {
        if (data == null || data.length == 0) {
            return new Decoded("", TEXT_CONTENT_TYPE, false, null, null);
        }

        // 1. Detect Confluent Schema Registry wire format
        if (com.eventore.schema.AvroPayloadDecoder.isConfluentWireFormat(data)) {
            int schemaId = com.eventore.schema.AvroPayloadDecoder.extractSchemaId(data);
            if (schemaRegistryClient != null) {
                java.util.Optional<com.eventore.schema.SchemaMetadata> metaOpt =
                        schemaRegistryClient.getSchemaById(registryUrl, schemaId);
                if (metaOpt.isPresent()) {
                    com.eventore.schema.SchemaMetadata meta = metaOpt.get();
                    try {
                        if ("AVRO".equalsIgnoreCase(meta.schemaType())) {
                            String json = com.eventore.schema.AvroPayloadDecoder.decode(data, meta.schemaContent());
                            return new Decoded(json, "application/json", false, schemaId, "AVRO");
                        } else if ("JSON".equalsIgnoreCase(meta.schemaType())
                                || "JSON_SCHEMA".equalsIgnoreCase(meta.schemaType())) {
                            byte[] jsonBytes = new byte[data.length - com.eventore.schema.AvroPayloadDecoder.WIRE_HEADER_SIZE];
                            System.arraycopy(
                                    data,
                                    com.eventore.schema.AvroPayloadDecoder.WIRE_HEADER_SIZE,
                                    jsonBytes,
                                    0,
                                    jsonBytes.length);
                            return new Decoded(
                                    new String(jsonBytes, StandardCharsets.UTF_8),
                                    "application/json",
                                    false,
                                    schemaId,
                                    "JSON");
                        }
                    } catch (Exception e) {
                        // Fall through to unresolved schema wire format
                    }
                }
            }
            return new Decoded(
                    Base64.getEncoder().encodeToString(data),
                    "application/vnd.apache.avro+binary; encoding=base64; schemaId=" + schemaId,
                    true,
                    schemaId,
                    "AVRO");
        }

        if (isValidUtf8(data)) {
            return new Decoded(new String(data, StandardCharsets.UTF_8), TEXT_CONTENT_TYPE, false, null, null);
        }
        return new Decoded(Base64.getEncoder().encodeToString(data), BASE64_CONTENT_TYPE, true, null, null);
    }

    static boolean isValidUtf8(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}

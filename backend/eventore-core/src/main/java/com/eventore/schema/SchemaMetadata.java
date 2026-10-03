package com.eventore.schema;

import java.util.Objects;

/**
 * Metadata for a schema registered in an external Schema Registry
 * (Confluent, Apicurio, AWS Glue) or locally in Eventore.
 */
public record SchemaMetadata(
        int schemaId,
        String subject,
        Integer version,
        String schemaType,
        String schemaContent) {

    public SchemaMetadata {
        schemaType = (schemaType == null || schemaType.isBlank()) ? "AVRO" : schemaType.toUpperCase();
    }

    public static SchemaMetadata avro(int schemaId, String subject, String schemaContent) {
        return new SchemaMetadata(schemaId, subject, 1, "AVRO", schemaContent);
    }

    public static SchemaMetadata avro(int schemaId, String schemaContent) {
        return new SchemaMetadata(schemaId, null, null, "AVRO", schemaContent);
    }

    public static SchemaMetadata json(int schemaId, String schemaContent) {
        return new SchemaMetadata(schemaId, null, null, "JSON", schemaContent);
    }
}

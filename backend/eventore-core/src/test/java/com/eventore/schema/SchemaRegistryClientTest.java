package com.eventore.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventore.connector.spi.PayloadCodec;
import java.io.IOException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class SchemaRegistryClientTest {

    private static final String PRODUCT_SCHEMA = """
            {
              "type": "record",
              "name": "Product",
              "fields": [
                {"name": "sku", "type": "string"},
                {"name": "price", "type": "double"}
              ]
            }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void schemaIdsAreScopedToRegistryAndRemoteErrorsNeverRegisterLocally() throws Exception {
        java.net.http.HttpClient http = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        org.mockito.Mockito.when(response.body()).thenReturn("{\"schema\":\"registry-a\"}", "{\"schema\":\"registry-b\"}");
        DefaultSchemaRegistryClient client = new DefaultSchemaRegistryClient(null, http, new com.fasterxml.jackson.databind.ObjectMapper());
        client.registerLocalSchema(7, "local", "AVRO");
        assertThat(client.getSchemaById("https://a.test", 7).orElseThrow().schemaContent()).isEqualTo("registry-a");
        assertThat(client.getSchemaById("https://b.test", 7).orElseThrow().schemaContent()).isEqualTo("registry-b");
        assertThat(client.getSchemaById(7).orElseThrow().schemaContent()).isEqualTo("local");
        org.mockito.Mockito.when(response.statusCode()).thenReturn(503);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.registerSchema("https://a.test", "subject", PRODUCT_SCHEMA, "AVRO"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(client.getCachedSchemas()).hasSize(3);
    }

    @Test
    void localRegistrationAndDecodingThroughPayloadCodec() throws IOException {
        DefaultSchemaRegistryClient client = new DefaultSchemaRegistryClient();
        int schemaId = 101;
        client.registerLocalSchema(schemaId, PRODUCT_SCHEMA, "AVRO");

        Schema schema = new Schema.Parser().parse(PRODUCT_SCHEMA);
        GenericRecord record = new GenericData.Record(schema);
        record.put("sku", "PROD-XYZ");
        record.put("price", 49.99);

        byte[] wireBytes = AvroPayloadDecoder.encode(record, schemaId);

        // Decode with registry client
        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(wireBytes, client, null);

        assertThat(decoded.schemaId()).isEqualTo(schemaId);
        assertThat(decoded.schemaType()).isEqualTo("AVRO");
        assertThat(decoded.contentType()).isEqualTo("application/json");
        assertThat(decoded.base64()).isFalse();
        assertThat(decoded.text()).contains("\"sku\": \"PROD-XYZ\"");
        assertThat(decoded.text()).contains("49.99");
    }

    @Test
    void wireFormatWithoutMatchingSchemaReturnsTaggedBase64() {
        DefaultSchemaRegistryClient client = new DefaultSchemaRegistryClient();
        byte[] wireBytes = new byte[] {0x00, 0x00, 0x01, 0x00, 0x05, 0x12, 0x34};

        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(wireBytes, client, null);

        assertThat(decoded.schemaId()).isEqualTo(65541);
        assertThat(decoded.schemaType()).isEqualTo("AVRO");
        assertThat(decoded.contentType()).startsWith("application/vnd.apache.avro+binary; encoding=base64; schemaId=65541");
        assertThat(decoded.base64()).isTrue();
        assertThat(PayloadCodec.toBytes(decoded.text(), decoded.contentType())).isEqualTo(wireBytes);
    }
}

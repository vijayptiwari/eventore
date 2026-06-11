package com.eventore.connector.kafka;

import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaClientSupportTest {

    @Test
    void clientPropsSetsBootstrapServers() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);

        Properties props = KafkaClientSupport.clientProps(profile);

        assertEquals("localhost:9092", props.get("bootstrap.servers"));
        assertFalse(props.containsKey("security.protocol"));
    }

    @Test
    void consumerPropsIncludesGroupAndSerializers() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);

        Properties props = KafkaClientSupport.consumerProps(profile, "my-group");

        assertEquals("my-group", props.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals(StringDeserializer.class.getName(), props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(ByteArrayDeserializer.class.getName(), props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("earliest", props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals("false", props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    }

    @Test
    void producerPropsIncludesSerializersAndAcks() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);

        Properties props = KafkaClientSupport.producerProps(profile);

        assertEquals(StringSerializer.class.getName(), props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(ByteArraySerializer.class.getName(), props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", props.get(ProducerConfig.ACKS_CONFIG));
    }

    @Test
    void clientPropsMergesDottedProfilePropertiesLast() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KAFKA,
                "localhost:9092",
                Map.of(
                        "saslMechanism", "PLAIN",
                        "security.protocol", "SASL_SSL",
                        "ssl.truststore.location", "/etc/kafka/truststore.jks",
                        "customCamelKey", "ignored"),
                Map.of("username", "u", "password", "p"));

        Properties props = KafkaClientSupport.clientProps(profile);

        // Explicit dotted Kafka configs win over derived security defaults.
        assertEquals("SASL_SSL", props.get("security.protocol"));
        assertEquals("/etc/kafka/truststore.jks", props.get("ssl.truststore.location"));
        // Eventore-specific camelCase keys are not copied into client props.
        assertFalse(props.containsKey("customCamelKey"));
        assertFalse(props.containsKey("saslMechanism"));
    }

    @Test
    void applySecurityConfiguresSaslPlain() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KAFKA,
                "localhost:9092",
                Map.of("saslMechanism", "PLAIN", "securityProtocol", "SASL_SSL"),
                Map.of("username", "user\"name", "password", "pass\\word"));

        Properties props = new Properties();
        KafkaClientSupport.applySecurity(profile, props);

        assertEquals("SASL_SSL", props.get("security.protocol"));
        assertEquals("PLAIN", props.get("sasl.mechanism"));
        String jaas = props.get("sasl.jaas.config").toString();
        assertTrue(jaas.contains("user\\\"name"));
        assertTrue(jaas.contains("pass\\\\word"));
    }
}

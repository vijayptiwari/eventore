package com.eventore.connector.kafka;

import com.eventore.domain.ConnectionProfile;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaClientSupport {

    private KafkaClientSupport() {}

    public static Properties clientProps(ConnectionProfile profile) {
        Properties props = new Properties();
        props.put("bootstrap.servers", profile.getBrokerUrl());
        applySecurity(profile, props);
        mergeProfileProperties(profile, props);
        return props;
    }

    /**
     * Copies native Kafka client configuration from the profile (dotted keys such as
     * {@code security.protocol} or {@code ssl.truststore.location}) into the client
     * properties last, so explicitly configured values win over derived defaults.
     * Eventore-specific camelCase keys (e.g. {@code saslMechanism}) are not Kafka
     * configs and are skipped.
     */
    private static void mergeProfileProperties(ConnectionProfile profile, Properties props) {
        profile.getProperties().forEach((key, value) -> {
            if (key != null && value != null && key.indexOf('.') >= 0) {
                props.put(key, value);
            }
        });
    }

    public static Properties consumerProps(ConnectionProfile profile, String group) {
        Properties props = clientProps(profile);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return props;
    }

    public static Properties producerProps(ConnectionProfile profile) {
        Properties props = clientProps(profile);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    public static void applySecurity(ConnectionProfile profile, Properties props) {
        String mechanism = profile.property("saslMechanism");
        if (mechanism != null && !mechanism.isBlank()) {
            props.put("security.protocol", profile.propertyOrDefault("securityProtocol", "SASL_PLAINTEXT"));
            props.put("sasl.mechanism", mechanism);
            String username = profile.credential("username");
            String password = profile.credential("password");
            if (username != null && password != null) {
                props.put(
                        "sasl.jaas.config",
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                                + escapeJaas(username) + "\" password=\"" + escapeJaas(password) + "\";");
            }
        }
    }

    private static String escapeJaas(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package com.eventore.provider.kafka;

import com.eventore.connector.kafka.KafkaMessagingConnector;
import com.eventore.inspect.kafka.KafkaMessagingInspector;
import com.eventore.domain.ProtocolType;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.KAFKA)
@ConditionalOnClass(KafkaMessagingConnector.class)
public class KafkaStreamProviderAutoConfiguration {

    @Bean
    StreamProvider kafkaStreamProvider(
            KafkaMessagingConnector connector, KafkaMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public com.eventore.domain.ProtocolType protocol() {
                return com.eventore.domain.ProtocolType.KAFKA;
            }

            @Override
            public KafkaMessagingConnector connector() {
                return connector;
            }

            @Override
            public java.util.Optional<com.eventore.inspect.spi.MessagingInspector> inspector() {
                return java.util.Optional.of(inspector);
            }

            @Override
            public String moduleId() {
                return "kafka";
            }
        };
    }
}

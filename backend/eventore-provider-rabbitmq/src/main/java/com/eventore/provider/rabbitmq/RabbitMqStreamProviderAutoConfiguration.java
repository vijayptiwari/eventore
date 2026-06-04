package com.eventore.provider.rabbitmq;

import com.eventore.connector.rabbitmq.RabbitMqMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.rabbitmq.RabbitMqMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.RABBITMQ)
@ConditionalOnClass(RabbitMqMessagingConnector.class)
public class RabbitMqStreamProviderAutoConfiguration {

    @Bean
    StreamProvider rabbitMqStreamProvider(
            RabbitMqMessagingConnector connector, RabbitMqMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.RABBITMQ;
            }

            @Override
            public RabbitMqMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

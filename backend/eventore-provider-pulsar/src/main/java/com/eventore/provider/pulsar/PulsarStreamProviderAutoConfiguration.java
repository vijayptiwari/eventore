package com.eventore.provider.pulsar;

import com.eventore.connector.pulsar.PulsarMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.pulsar.PulsarMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.PULSAR)
@ConditionalOnClass(PulsarMessagingConnector.class)
public class PulsarStreamProviderAutoConfiguration {

    @Bean
    StreamProvider pulsarStreamProvider(
            PulsarMessagingConnector connector, PulsarMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.PULSAR;
            }

            @Override
            public PulsarMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

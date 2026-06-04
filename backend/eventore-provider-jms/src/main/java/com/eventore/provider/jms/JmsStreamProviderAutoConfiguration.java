package com.eventore.provider.jms;

import com.eventore.connector.jms.JmsMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.jms.JmsMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.JMS)
@ConditionalOnClass(JmsMessagingConnector.class)
public class JmsStreamProviderAutoConfiguration {

    @Bean
    StreamProvider jmsStreamProvider(
            JmsMessagingConnector connector, JmsMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.JMS;
            }

            @Override
            public JmsMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

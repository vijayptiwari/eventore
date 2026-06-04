package com.eventore.provider.kinesis;

import com.eventore.connector.kinesis.KinesisMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.kinesis.KinesisMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.KINESIS)
@ConditionalOnClass(KinesisMessagingConnector.class)
public class KinesisStreamProviderAutoConfiguration {

    @Bean
    StreamProvider kinesisStreamProvider(
            KinesisMessagingConnector connector, KinesisMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.KINESIS;
            }

            @Override
            public KinesisMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

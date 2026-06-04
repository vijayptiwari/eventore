package com.eventore.provider.gcp;

import com.eventore.connector.gcp.GcpPubSubMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.gcp.GcpPubSubMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.GCP_PUBSUB)
@ConditionalOnClass(GcpPubSubMessagingConnector.class)
public class GcpPubSubStreamProviderAutoConfiguration {

    @Bean
    StreamProvider gcpPubSubStreamProvider(
            GcpPubSubMessagingConnector connector, GcpPubSubMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.GCP_PUBSUB;
            }

            @Override
            public GcpPubSubMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

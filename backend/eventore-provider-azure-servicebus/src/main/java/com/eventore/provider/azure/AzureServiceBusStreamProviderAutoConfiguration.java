package com.eventore.provider.azure;

import com.eventore.connector.azure.AzureServiceBusMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.azure.AzureServiceBusMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.AZURE_SERVICE_BUS)
@ConditionalOnClass(AzureServiceBusMessagingConnector.class)
public class AzureServiceBusStreamProviderAutoConfiguration {

    @Bean
    StreamProvider azureServiceBusStreamProvider(
            AzureServiceBusMessagingConnector connector,
            AzureServiceBusMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.AZURE_SERVICE_BUS;
            }

            @Override
            public AzureServiceBusMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

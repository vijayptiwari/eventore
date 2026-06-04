package com.eventore.provider.mqtt;

import com.eventore.connector.mqtt.MqttMessagingConnector;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.mqtt.MqttMessagingInspector;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.provider.OnEnabledProtocol;
import com.eventore.provider.StreamProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@OnEnabledProtocol(ProtocolType.MQTT)
@ConditionalOnClass(MqttMessagingConnector.class)
public class MqttStreamProviderAutoConfiguration {

    @Bean
    StreamProvider mqttStreamProvider(
            MqttMessagingConnector connector, MqttMessagingInspector inspector) {
        return new StreamProvider() {
            @Override
            public ProtocolType protocol() {
                return ProtocolType.MQTT;
            }

            @Override
            public MqttMessagingConnector connector() {
                return connector;
            }

            @Override
            public Optional<MessagingInspector> inspector() {
                return Optional.of(inspector);
            }
        };
    }
}

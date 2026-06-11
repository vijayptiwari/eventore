package com.eventore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProtocolTypeTest {

    @Test
    void allProtocolTypesExist() {
        assertThat(ProtocolType.values())
                .containsExactly(
                        ProtocolType.KAFKA,
                        ProtocolType.MQTT,
                        ProtocolType.JMS,
                        ProtocolType.PULSAR,
                        ProtocolType.RABBITMQ,
                        ProtocolType.KINESIS,
                        ProtocolType.GCP_PUBSUB,
                        ProtocolType.AZURE_SERVICE_BUS);
    }
}

package com.eventore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicRefTest {

    @Test
    void constructorSetsFields() {
        TopicRef ref = new TopicRef("orders", "topic", ProtocolType.KAFKA);
        assertThat(ref.getName()).isEqualTo("orders");
        assertThat(ref.getType()).isEqualTo("topic");
        assertThat(ref.getProtocol()).isEqualTo(ProtocolType.KAFKA);
    }

    @Test
    void settersUpdateFields() {
        TopicRef ref = new TopicRef();
        ref.setName("events");
        ref.setType("queue");
        ref.setProtocol(ProtocolType.RABBITMQ);
        assertThat(ref.getName()).isEqualTo("events");
        assertThat(ref.getType()).isEqualTo("queue");
        assertThat(ref.getProtocol()).isEqualTo(ProtocolType.RABBITMQ);
    }
}

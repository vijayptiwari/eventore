package com.eventore.connector.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitMqBrokerUrlsTest {

    @Test
    void parsesPlainHost() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("rabbit.internal");
        assertThat(e.host()).isEqualTo("rabbit.internal");
        assertThat(e.port()).isEqualTo(RabbitMqBrokerUrls.DEFAULT_PORT);
        assertThat(e.tls()).isFalse();
    }

    @Test
    void parsesHostAndPort() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("rabbit.internal:5673");
        assertThat(e.host()).isEqualTo("rabbit.internal");
        assertThat(e.port()).isEqualTo(5673);
    }

    @Test
    void parsesAmqpUri() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("amqp://rabbit.internal:5673/vhost");
        assertThat(e.host()).isEqualTo("rabbit.internal");
        assertThat(e.port()).isEqualTo(5673);
        assertThat(e.tls()).isFalse();
    }

    @Test
    void parsesAmqpUriWithoutPortUsingDefault() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("amqp://rabbit.internal");
        assertThat(e.port()).isEqualTo(RabbitMqBrokerUrls.DEFAULT_PORT);
    }

    @Test
    void parsesAmqpsUriWithTlsDefaults() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("amqps://secure.rabbit.cloud");
        assertThat(e.host()).isEqualTo("secure.rabbit.cloud");
        assertThat(e.port()).isEqualTo(RabbitMqBrokerUrls.DEFAULT_TLS_PORT);
        assertThat(e.tls()).isTrue();
    }

    @Test
    void parsesUriWithCredentials() {
        RabbitMqBrokerUrls.Endpoint e = RabbitMqBrokerUrls.parse("amqp://user:pass@rabbit.internal:5672");
        assertThat(e.host()).isEqualTo("rabbit.internal");
        assertThat(e.port()).isEqualTo(5672);
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> RabbitMqBrokerUrls.parse("http://rabbit.internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void rejectsBlankAndInvalidPort() {
        assertThatThrownBy(() -> RabbitMqBrokerUrls.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RabbitMqBrokerUrls.parse("host:notaport"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }
}

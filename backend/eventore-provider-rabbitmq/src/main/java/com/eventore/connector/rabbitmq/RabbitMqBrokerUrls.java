package com.eventore.connector.rabbitmq;

import java.net.URI;

/**
 * Parses RabbitMQ broker URLs into host/port/TLS. Accepts plain "host",
 * "host:port", and full "amqp://" / "amqps://" URIs (the previous naive
 * host:port split broke on scheme-prefixed URLs).
 */
public final class RabbitMqBrokerUrls {

    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_TLS_PORT = 5671;

    private RabbitMqBrokerUrls() {}

    public record Endpoint(String host, int port, boolean tls) {}

    public static Endpoint parse(String brokerUrl) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ broker URL is required");
        }
        String url = brokerUrl.trim();
        if (url.contains("://")) {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "amqp";
            boolean tls = scheme.equals("amqps");
            if (!scheme.equals("amqp") && !scheme.equals("amqps")) {
                throw new IllegalArgumentException(
                        "Unsupported RabbitMQ scheme '" + scheme + "' (expected amqp or amqps)");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("RabbitMQ broker URL has no host: " + brokerUrl);
            }
            int port = uri.getPort() != -1 ? uri.getPort() : (tls ? DEFAULT_TLS_PORT : DEFAULT_PORT);
            return new Endpoint(host, port, tls);
        }
        int idx = url.lastIndexOf(':');
        if (idx > 0 && idx < url.length() - 1) {
            String host = url.substring(0, idx);
            try {
                return new Endpoint(host, Integer.parseInt(url.substring(idx + 1)), false);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid RabbitMQ port in broker URL: " + brokerUrl, e);
            }
        }
        return new Endpoint(url, DEFAULT_PORT, false);
    }
}

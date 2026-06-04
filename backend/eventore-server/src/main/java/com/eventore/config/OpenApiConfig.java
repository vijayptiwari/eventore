package com.eventore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI eventoreOpenApi(@Value("${eventore.openapi.server-url:/}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Eventore API")
                        .description(
                                "Multi-stream messaging console. Modular providers: Kafka, Pulsar, RabbitMQ, MQTT, JMS, "
                                        + "Kinesis, GCP Pub/Sub, Azure Service Bus. Canonical contract: "
                                        + "classpath:openapi/eventore-api.yaml")
                        .version("0.1.0")
                        .contact(new Contact().name("Eventore").url("https://github.com/eventore")))
                .servers(List.of(new Server().url(serverUrl).description("Eventore server")));
    }
}

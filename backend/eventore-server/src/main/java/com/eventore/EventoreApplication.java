package com.eventore;

import com.eventore.config.EventoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EventoreProperties.class)
public class EventoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventoreApplication.class, args);
    }
}

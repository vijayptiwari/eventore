package com.eventore.provider;

import com.eventore.domain.ProtocolType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnEnabledProtocolCondition implements Condition {

    static final String PROPERTY = "eventore.enabled-protocols";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ProtocolType required = (ProtocolType)
                metadata.getAnnotationAttributes(OnEnabledProtocol.class.getName()).get("value");
        String raw = context.getEnvironment().getProperty(PROPERTY, "").trim();
        if (raw.isEmpty()) {
            return true;
        }
        Set<String> enabled = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return enabled.contains(required.name());
    }
}

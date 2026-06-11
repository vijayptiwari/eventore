package com.eventore.provider;

import com.eventore.domain.ProtocolType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnEnabledProtocolCondition implements Condition {

    static final String PROPERTY = "eventore.enabled-protocols";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(OnEnabledProtocol.class.getName());
        if (attributes == null || !(attributes.get("value") instanceof ProtocolType required)) {
            throw new IllegalStateException(
                    "@OnEnabledProtocol annotation attributes missing or invalid on " + metadata);
        }
        String raw = context.getEnvironment().getProperty(PROPERTY, "").trim();
        if (raw.isEmpty()) {
            return true;
        }
        Set<String> enabled = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return enabled.contains(required.name());
    }
}

package com.eventore.provider;

import com.eventore.domain.ProtocolType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Auto-configuration for a stream provider is active only when that protocol is enabled
 * via {@code eventore.enabled-protocols} (or when the list is empty = all modules on classpath).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnEnabledProtocolCondition.class)
public @interface OnEnabledProtocol {

    ProtocolType value();
}

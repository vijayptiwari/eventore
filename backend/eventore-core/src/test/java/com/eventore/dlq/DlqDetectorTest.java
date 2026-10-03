package com.eventore.dlq;

import com.eventore.domain.UnifiedMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlqDetectorTest {

    @Test
    void detectsStandardDlqPatterns() {
        assertThat(DlqDetector.isDlqTopic("orders.DLQ")).isTrue();
        assertThat(DlqDetector.isDlqTopic("orders-dlq")).isTrue();
        assertThat(DlqDetector.isDlqTopic("orders_dlq")).isTrue();
        assertThat(DlqDetector.isDlqTopic("payments.dlt")).isTrue();
        assertThat(DlqDetector.isDlqTopic("payments-dlt")).isTrue();
        assertThat(DlqDetector.isDlqTopic("notifications.deadletter")).isTrue();
        assertThat(DlqDetector.isDlqTopic("notifications-dead-letter")).isTrue();
    }

    @Test
    void rejectsNonDlqTopics() {
        assertThat(DlqDetector.isDlqTopic("orders")).isFalse();
        assertThat(DlqDetector.isDlqTopic("payments_v1")).isFalse();
        assertThat(DlqDetector.isDlqTopic("dlq-service-heartbeat")).isFalse();
        assertThat(DlqDetector.isDlqTopic(null)).isFalse();
        assertThat(DlqDetector.isDlqTopic("")).isFalse();
    }

    @Test
    void infersTargetTopicsAccurately() {
        assertThat(DlqDetector.inferTargetTopic("orders.DLQ")).isEqualTo("orders");
        assertThat(DlqDetector.inferTargetTopic("orders-dlq")).isEqualTo("orders");
        assertThat(DlqDetector.inferTargetTopic("orders_dlq")).isEqualTo("orders");
        assertThat(DlqDetector.inferTargetTopic("payments-service.dlt")).isEqualTo("payments-service");
        assertThat(DlqDetector.inferTargetTopic("users.deadletter")).isEqualTo("users");
        assertThat(DlqDetector.inferTargetTopic("regular-topic")).isEqualTo("regular-topic");
    }

    @Test
    void supportsCustomRegexPattern() {
        String customPattern = ".*_poison_pill$";
        assertThat(DlqDetector.isDlqTopic("telemetry_poison_pill", customPattern)).isTrue();
        assertThat(DlqDetector.isDlqTopic("telemetry_clean", customPattern)).isFalse();
    }

    @Test
    void extractsDlqMessageInfoFromHeaders() {
        UnifiedMessage message = new UnifiedMessage();
        message.setId("msg-123");
        message.setPayload("{\"amount\":-1}");
        message.setHeaders(Map.of(
                "x-original-topic", "orders",
                "x-exception-message", "Negative amount not allowed",
                "x-exception-fqcn", "java.lang.IllegalArgumentException",
                "x-exception-stacktrace", "java.lang.IllegalArgumentException: Negative amount\n\tat OrderService.process"
        ));

        DlqMessageInfo info = DlqMessageInfo.from(message);
        assertThat(info.originalTopic()).isEqualTo("orders");
        assertThat(info.failureReason()).isEqualTo("Negative amount not allowed");
        assertThat(info.exceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(info.stackTraceSnippet()).contains("OrderService.process");
    }
}

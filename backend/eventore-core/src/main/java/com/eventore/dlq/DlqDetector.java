package com.eventore.dlq;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for detecting Dead Letter Queue (DLQ / DLT) topics and inferring original target topics.
 */
public final class DlqDetector {

    // Common enterprise naming conventions:
    // orders.DLQ, orders-dlq, orders_dlq, orders.dlt, orders-dlt, orders.deadletter, orders-dead-letter, orders_deadletter
    private static final Pattern DEFAULT_DLQ_PATTERN = Pattern.compile(
            "^(.*?)([-._](?:dlq|dlt|dead[-_]?letter))$",
            Pattern.CASE_INSENSITIVE);

    private DlqDetector() {}

    /**
     * Determines whether a topic name matches DLQ patterns.
     */
    public static boolean isDlqTopic(String topicName) {
        return isDlqTopic(topicName, null);
    }

    /**
     * Determines whether a topic name matches DLQ patterns or an optional custom regex.
     */
    public static boolean isDlqTopic(String topicName, String customPattern) {
        if (topicName == null || topicName.isBlank()) {
            return false;
        }
        if (customPattern != null && !customPattern.isBlank()) {
            try {
                if (Pattern.compile(customPattern, Pattern.CASE_INSENSITIVE).matcher(topicName).find()) {
                    return true;
                }
            } catch (Exception ignored) {
                // fall back to default pattern
            }
        }
        return DEFAULT_DLQ_PATTERN.matcher(topicName).matches();
    }

    /**
     * Infers the original primary target topic from a DLQ topic name.
     */
    public static String inferTargetTopic(String dlqTopic) {
        return inferTargetTopic(dlqTopic, null);
    }

    /**
     * Infers the original primary target topic from a DLQ topic name, with optional custom regex pattern.
     */
    public static String inferTargetTopic(String dlqTopic, String customPattern) {
        if (dlqTopic == null || dlqTopic.isBlank()) {
            return "";
        }
        Matcher matcher = DEFAULT_DLQ_PATTERN.matcher(dlqTopic);
        if (matcher.matches() && !matcher.group(1).isBlank()) {
            return matcher.group(1);
        }
        return dlqTopic;
    }
}

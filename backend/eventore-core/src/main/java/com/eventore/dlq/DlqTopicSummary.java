package com.eventore.dlq;

/**
 * Summary of a detected Dead Letter Queue topic or queue.
 */
public record DlqTopicSummary(
        String dlqTopic,
        String inferredTargetTopic,
        long partitionCount,
        String detectionReason) {}

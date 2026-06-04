package com.eventore.domain;

/** Managed or hosted streaming product (maps to protocol + connection properties). */
public enum StreamPlatform {
    GENERIC,
    AWS_MSK,
    AWS_KINESIS,
    AWS_MQ,
    AWS_IOT_CORE,
    AWS_SQS,
    AZURE_EVENT_HUBS,
    AZURE_SERVICE_BUS,
    AZURE_IOT_HUB,
    GCP_PUBSUB,
    GCP_MANAGED_KAFKA,
    OCP_AMQ_STREAMS,
    OCP_STRIMZI_KAFKA
}

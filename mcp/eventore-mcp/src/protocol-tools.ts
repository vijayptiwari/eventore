import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { EventoreClient } from './eventore-client.js';

function inspectToolResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

export function registerProtocolTools(server: McpServer, client: EventoreClient): void {
  server.tool(
    'eventore_rabbitmq_list_queues',
    'RabbitMQ: list queues (uses inspect topics API).',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_rabbitmq_queue_detail',
    'RabbitMQ: queue depth and consumer metadata for one queue.',
    { connectionId: z.string(), queue: z.string() },
    async (args) => inspectToolResult(await client.inspectTopic(args.connectionId, args.queue)),
  );

  server.tool(
    'eventore_rabbitmq_queue_depth',
    'RabbitMQ: approximate backlog (messages ready) for a queue.',
    { connectionId: z.string(), queue: z.string() },
    async (args) =>
      inspectToolResult(await client.inspectLag(args.connectionId, args.queue, args.queue)),
  );

  server.tool(
    'eventore_gcp_list_topics',
    'GCP Pub/Sub: list topics in the project.',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_gcp_list_subscriptions',
    'GCP Pub/Sub: list subscriptions with topic attributes.',
    { connectionId: z.string() },
    async (args) => inspectToolResult(await client.inspectConsumerGroups(args.connectionId)),
  );

  server.tool(
    'eventore_gcp_subscription_backlog',
    'GCP Pub/Sub: backlog for a subscription (groupId = subscription name).',
    {
      connectionId: z.string(),
      subscription: z.string(),
      topicFilter: z.string().optional(),
    },
    async (args) =>
      inspectToolResult(
        await client.inspectLag(args.connectionId, args.subscription, args.topicFilter),
      ),
  );

  server.tool(
    'eventore_azure_list_entities',
    'Azure Service Bus: list queues and topics.',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_azure_list_subscriptions',
    'Azure Service Bus: list topic subscriptions with active/DLQ counts.',
    { connectionId: z.string() },
    async (args) => inspectToolResult(await client.inspectConsumerGroups(args.connectionId)),
  );

  server.tool(
    'eventore_azure_peek_messages',
    'Azure Service Bus: peek messages without removing (topic needs partition=subscription name).',
    {
      connectionId: z.string(),
      entity: z.string(),
      subscription: z.string().optional(),
      maxMessages: z.number().int().min(1).max(100).optional().default(10),
    },
    async (args) =>
      inspectToolResult(
        await client.inspectSearch(args.connectionId, {
          topic: args.entity,
          partition: args.subscription,
          maxMessages: args.maxMessages,
        }),
      ),
  );

  server.tool(
    'eventore_azure_entity_backlog',
    'Azure Service Bus: active message count for queue or subscription.',
    {
      connectionId: z.string(),
      entityId: z.string(),
      topicFilter: z.string().optional(),
    },
    async (args) =>
      inspectToolResult(await client.inspectLag(args.connectionId, args.entityId, args.topicFilter)),
  );

  server.tool(
    'eventore_diagnostics_subscriptions',
    'Operator triage: active subscription health snapshot from diagnostics API.',
    {},
    async () => inspectToolResult(await client.diagnosticsSubscriptions()),
  );

  server.tool(
    'eventore_mqtt_list_topics',
    'MQTT: list topics (optional filter).',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_mqtt_topic_detail',
    'MQTT: describe one topic or subscription filter.',
    { connectionId: z.string(), topic: z.string() },
    async (args) => inspectToolResult(await client.inspectTopic(args.connectionId, args.topic)),
  );

  server.tool(
    'eventore_jms_list_destinations',
    'JMS: list queues and topics on the broker.',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_jms_destination_detail',
    'JMS: metadata for one queue or topic destination.',
    { connectionId: z.string(), destination: z.string() },
    async (args) => inspectToolResult(await client.inspectTopic(args.connectionId, args.destination)),
  );

  server.tool(
    'eventore_pulsar_list_topics',
    'Pulsar: list topics in the tenant namespace.',
    { connectionId: z.string(), filter: z.string().optional() },
    async (args) => inspectToolResult(await client.inspectTopics(args.connectionId, args.filter)),
  );

  server.tool(
    'eventore_pulsar_subscription_backlog',
    'Pulsar: subscription backlog for a topic (groupId = subscription name).',
    {
      connectionId: z.string(),
      topic: z.string(),
      subscription: z.string(),
    },
    async (args) =>
      inspectToolResult(await client.inspectLag(args.connectionId, args.subscription, args.topic)),
  );
}

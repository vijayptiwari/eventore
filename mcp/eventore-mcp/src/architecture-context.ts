/** Static architecture summary for MCP resources and agent onboarding. */
export const MCP_ARCHITECTURE = {
  product: 'Eventore',
  planes: {
    control: {
      purpose: 'Provider registration, UI cascade metadata, deployment policy. No broker TCP.',
      apiPrefix: '/api/v1/control',
      mcpTools: [
        'eventore_get_control_plane',
        'eventore_list_providers',
        'eventore_get_provider',
        'eventore_get_provider_status',
        'eventore_register_provider',
        'eventore_deregister_provider',
      ],
    },
    data: {
      purpose: 'Connections, publish/subscribe, inspect, Kafka admin. All broker I/O via backend.',
      apiPrefix: '/api/v1/connections',
      mcpTools: [
        'eventore_list_connections',
        'eventore_create_connection',
        'eventore_publish_message',
        'eventore_consume_messages',
        'eventore_inspect_*',
        'eventore_kafka_*',
      ],
    },
  },
  mcpRole:
    'Optional third workload (Node). Proxies agents to the backend; never embeds broker clients.',
  configEntry: 'GET /api/v1/config embeds controlPlane.uiCascade and loadedModules.',
  agentWorkflow: [
    '1. eventore_get_config or read eventore://config',
    '2. eventore_get_control_plane — confirm protocol is registered before connections',
    '3. eventore_protocol_guide or eventore_quick_probe for discovery',
    '4. Data-plane tools for publish/consume/inspect',
  ],
} as const;

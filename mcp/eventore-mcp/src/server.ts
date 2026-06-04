import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { EventoreClient } from './eventore-client.js';
import { registerResources } from './resources.js';
import { registerTools } from './tools.js';

export function createEventoreMcpServer(apiBaseUrl: string): McpServer {
  const client = new EventoreClient(apiBaseUrl);
  const server = new McpServer(
    {
      name: 'eventore-mcp',
      version: '0.1.0',
    },
    {
      capabilities: {
        tools: {},
        resources: {},
      },
      instructions: `Eventore MCP bridges AI agents to Kafka, MQTT, JMS, Pulsar, and RabbitMQ via the Eventore API.
Use eventore_protocol_guide or eventore_quick_probe before connecting to unfamiliar brokers.
Deployment mode on the Eventore instance (ADMIN/DEV/READONLY) controls whether publish and connection CRUD are allowed.
Set EVENTORE_API_URL to the Eventore backend (e.g. http://localhost:8080/api/v1).`,
    },
  );

  registerTools(server, client);
  registerResources(server, client);

  return server;
}

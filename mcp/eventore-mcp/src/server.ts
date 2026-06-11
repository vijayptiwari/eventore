import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { EventoreClient } from './eventore-client.js';
import { registerControlTools } from './control-tools.js';
import { registerPrompts } from './prompts.js';
import { registerResources } from './resources.js';
import { registerTools } from './tools.js';

export function createEventoreMcpServer(apiBaseUrl: string, apiToken?: string): McpServer {
  const client = new EventoreClient(apiBaseUrl, apiToken);
  const server = new McpServer(
    {
      name: 'eventore-mcp',
      version: '0.2.0',
    },
    {
      capabilities: {
        tools: {},
        resources: {},
        prompts: {},
      },
      instructions: `Eventore MCP is an optional agent adapter to the Eventore backend — not a broker client.

Architecture:
- Control plane (/api/v1/control): provider registration, UI cascade, revisioned snapshot. Tools: eventore_get_control_plane, eventore_*_provider*.
- Data plane (/api/v1/connections): connections, publish, subscribe (SSE), inspect, Kafka admin.

Always start with eventore_get_config or prompt eventore_discover, then confirm protocols are registered before creating connections.
Deployment mode (ADMIN/DEV/READONLY) gates publish and connection CRUD on the backend.
Set EVENTORE_API_URL (e.g. http://localhost:8080/api/v1). When the backend requires auth, set EVENTORE_API_TOKEN (Bearer on REST and SSE). Resources: eventore://architecture, eventore://control-plane.`,
    },
  );

  registerTools(server, client);
  registerControlTools(server, client);
  registerResources(server, client);
  registerPrompts(server, client);

  return server;
}

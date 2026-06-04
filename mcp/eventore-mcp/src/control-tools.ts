import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { EventoreClient } from './eventore-client.js';
import { ProtocolSchema, type ProtocolType } from './protocol-types.js';

function jsonResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

export function registerControlTools(server: McpServer, client: EventoreClient): void {
  server.tool(
    'eventore_get_control_plane',
    'Control plane: revisioned snapshot of registered providers, active protocols, and UI cascade (no broker I/O).',
    {},
    async () => jsonResult(await client.getControlPlane()),
  );

  server.tool(
    'eventore_list_providers',
    'Control plane: list stream provider descriptors (module id, capabilities, lifecycle state).',
    {},
    async () => jsonResult(await client.listProviders()),
  );

  server.tool(
    'eventore_get_provider',
    'Control plane: metadata for one protocol provider.',
    { protocol: ProtocolSchema },
    async (args) => jsonResult(await client.getProvider(args.protocol as ProtocolType)),
  );

  server.tool(
    'eventore_get_provider_status',
    'Control plane: registration state and whether the data plane can route this protocol.',
    { protocol: ProtocolSchema },
    async (args) => jsonResult(await client.getProviderStatus(args.protocol as ProtocolType)),
  );

  server.tool(
    'eventore_register_provider',
    'Control plane: register a provider at runtime (ADMIN). Required before data-plane use if not auto-registered.',
    { protocol: ProtocolSchema },
    async (args) => jsonResult(await client.registerProvider(args.protocol as ProtocolType)),
  );

  server.tool(
    'eventore_deregister_provider',
    'Control plane: deregister a provider (ADMIN). Fails if it is the last active provider.',
    { protocol: ProtocolSchema },
    async (args) => jsonResult(await client.deregisterProvider(args.protocol as ProtocolType)),
  );
}

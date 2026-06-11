# Eventore MCP Server

Optional **Model Context Protocol (MCP)** adapter for AI agents. Exposes Eventore **control plane** (provider registration, UI cascade) and **data plane** (connections, publish, inspect) through MCP tools, resources, and prompts.

## Architecture

```text
  AI Agent (Cursor, Claude, HTTP MCP client)
        |  MCP (stdio or HTTP :3100)
        v
  eventore-mcp  ---- REST + SSE ---->  Eventore Backend
        |                              |
        |                    control plane (/api/v1/control)
        |                    data plane   (/api/v1/connections)
        v                              v
                              Kafka / MQTT / JMS / Pulsar / RabbitMQ / cloud streams
```

The MCP process **never** opens broker connections. Deployment mode (ADMIN / DEV / READONLY) is enforced on the backend.

## Tools (28)

### Control plane

| Tool | Description |
|------|-------------|
| `eventore_get_control_plane` | Revisioned snapshot, active protocols, UI cascade |
| `eventore_list_providers` | All provider descriptors |
| `eventore_get_provider` | One protocol metadata |
| `eventore_get_provider_status` | Registration and data-plane routability |
| `eventore_register_provider` | Runtime register (ADMIN) |
| `eventore_deregister_provider` | Runtime deregister (ADMIN) |

### Data plane — core

| Tool | Description |
|------|-------------|
| `eventore_get_config` | Mode, actions, modules, embedded `controlPlane` |
| `eventore_list_connections` | Connection profiles |
| `eventore_create_connection` | Create profile |
| `eventore_update_connection` | Update profile |
| `eventore_delete_connection` | Delete profile |
| `eventore_validate_connection` | Connectivity test |
| `eventore_list_destinations` | Topics / queues |
| `eventore_publish_message` | Unified publish |
| `eventore_consume_messages` | SSE sample window |
| `eventore_protocol_guide` | Hints or NL protocol suggestion |
| `eventore_quick_probe` | Ephemeral probe workflow |

### Data plane — inspect & Kafka

| Tool | Description |
|------|-------------|
| `eventore_inspect_capabilities` | Inspect feature tokens for a connection |
| `eventore_inspect_topics` | List topics or queues (optional filter) |
| `eventore_inspect_topic` | Describe one topic or queue |
| `eventore_inspect_cluster` | Cluster metadata |
| `eventore_inspect_consumer_groups` | Groups / subscriptions |
| `eventore_inspect_lag` | Consumer lag |
| `eventore_inspect_search` | Topic search |
| `eventore_kinesis_list_shards` | Kinesis shard listing (requires KINESIS in adminProtocols) |
| `eventore_kafka_publish` | Kafka produce with key/partition |
| `eventore_kafka_create_topic` | Create topic |
| `eventore_kafka_delete_topic` | Delete topic |
| `eventore_kafka_list_acls` | List ACLs |

## Resources

| URI | Content |
|-----|---------|
| `eventore://architecture` | Plane map and agent workflow |
| `eventore://config` | Live `GET /config` |
| `eventore://control-plane` | Live `GET /control/plane` |
| `eventore://providers` | Live provider list |
| `eventore://connections` | Live connections |
| `eventore://protocol-guides` | Static hints (8 protocol types) |

## Prompts

| Prompt | Purpose |
|--------|---------|
| `eventore_discover` | Onboarding: config + control plane + architecture |
| `eventore_probe_broker` | Playbook for `eventore_quick_probe` |
| `eventore_kafka_inspection` | Cluster → groups → lag → search |
| `eventore_control_plane_ops` | Register / deregister / status checklist |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `EVENTORE_API_URL` | `http://localhost:8080/api/v1` | Backend API base |
| `EVENTORE_API_TOKEN` | (unset) | Backend API token (`Authorization: Bearer` on REST and SSE consume) |
| `MCP_TRANSPORT` | `stdio` | `stdio` or `http` |
| `MCP_PORT` | `3100` | HTTP port |
| `MCP_AUTH_TOKEN` | (unset) | Bearer on `/mcp` (HTTP only) |

## Local usage (stdio)

```bash
cd mcp/eventore-mcp
npm install && npm run build
```

See `cursor-mcp.json.example` for Cursor MCP settings.

## HTTP / Docker / Helm

```bash
docker build -f docker/Dockerfile.mcp -t eventore-mcp .
docker run -p 3100:3100 -e MCP_TRANSPORT=http \
  -e EVENTORE_API_URL=http://host.docker.internal:8080/api/v1 eventore-mcp
```

```bash
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://eventore-backend:8080/api/v1
```

Product documentation: [MCP for AI agents](https://vijayptiwari.github.io/eventore/guide/mcp.html).

# Eventore MCP Server

Optional **Model Context Protocol (MCP)** layer for AI agents (Cursor, Claude Desktop, custom automations) to interact with messaging buses **through the Eventore API**. Deploy it separately from the main console when you only need programmatic/agent access.

## Architecture

```text
  AI Agent (Cursor, etc.)
        |  MCP (stdio or HTTP)
        v
  eventore-mcp  -------- REST/SSE -------->  Eventore Backend  ---->  Kafka / MQTT / JMS / Pulsar / RabbitMQ
```

The MCP server does **not** embed broker clients. It delegates to Eventore so deployment modes (ADMIN / DEV / READONLY) and connectors stay centralized.

## Tools

| Tool | Purpose |
|------|---------|
| `eventore_get_config` | Deployment mode and allowed actions |
| `eventore_list_connections` | List connection profiles |
| `eventore_create_connection` | Register a broker connection |
| `eventore_delete_connection` | Remove a connection |
| `eventore_validate_connection` | Test connectivity |
| `eventore_list_destinations` | Topics / queues / exchanges |
| `eventore_publish_message` | Publish payload |
| `eventore_consume_messages` | Short-lived subscribe + SSE sample |
| `eventore_protocol_guide` | Smart hints per protocol or from natural-language hint |
| `eventore_quick_probe` | Create → validate → list → optional sample → delete |

## Resources

- `eventore://config`
- `eventore://connections`
- `eventore://protocol-guides`

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `EVENTORE_API_URL` | `http://localhost:8080/api/v1` | Eventore backend API base |
| `MCP_TRANSPORT` | `stdio` | `stdio` (local) or `http` (remote/K8s) |
| `MCP_PORT` | `3100` | HTTP listen port when `MCP_TRANSPORT=http` |
| `MCP_AUTH_TOKEN` | (unset) | If set, requires `Authorization: Bearer <token>` on HTTP |

## Local usage (stdio)

1. Start Eventore backend (`mvn spring-boot:run` in `backend/`).
2. Build MCP server:

```bash
cd mcp/eventore-mcp
npm install && npm run build
```

3. Add to Cursor **MCP settings** (see `cursor-mcp.json.example`):

```json
{
  "mcpServers": {
    "eventore": {
      "command": "node",
      "args": ["C:/path/to/eventore/mcp/eventore-mcp/dist/index.js"],
      "env": {
        "EVENTORE_API_URL": "http://localhost:8080/api/v1"
      }
    }
  }
}
```

## HTTP deployment (separate container)

```bash
docker build -f docker/Dockerfile.mcp -t eventore/mcp:0.1.0 .
docker run -p 3100:3100 \
  -e MCP_TRANSPORT=http \
  -e EVENTORE_API_URL=http://host.docker.internal:8080/api/v1 \
  eventore/mcp:0.1.0
```

Health: `GET http://localhost:3100/health`

MCP endpoint: `POST/GET http://localhost:3100/mcp` (Streamable HTTP transport)

## Helm (separate chart)

```bash
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://eventore-dev-backend:8080/api/v1
```

Point `eventore.apiUrl` at your existing Eventore backend Service. MCP pods do not need direct broker network access if the backend already has it.

## When to deploy MCP

- **Yes:** Agents in Cursor/CI need to publish, consume samples, or probe brokers without the React UI.
- **No:** Human operators only use the web console; skip MCP to reduce surface area.

EventOre 0.2.1 is the first unified versioned distribution of the console, backend providers, MCP server, and Helm charts.

### Included

- Eight messaging providers, live streaming, inspection, and deployment modes.
- Enterprise identity (OIDC / OAuth2 / JWT Bearer token validation) and multi-tenant workspaces with claim-based RBAC (`PLATFORM_ADMIN`, `WORKSPACE_ADMIN`, `OPERATOR`, `VIEWER`).
- Distributed subscription registry and cluster message fan-out bus (`SubscriptionDistributionBus` SPI: `LOCAL` and `REDIS` pub/sub) for stateless horizontal multi-replica scaling.
- Autonomous incident triage MCP tool (`eventore_triage_incident`) and prompt playbook (`eventore_incident_triage`) for automated multi-domain root-cause analysis.
- Kafka schema workflows, dead-letter inspection, redrive, and replay tools.
- Message masking, trace-context integration, consumer lag visualization, and cross-broker bridging.
- Externalized connection persistence options (file, JDBC with optimistic locking, Kubernetes CRD) and expanded user, configuration, deployment, and troubleshooting guides.
- Corrected provider bundle packaging, with CI verifying included providers and their administration delegates for all ten published variants.

### Install

```sh
helm install eventore oci://ghcr.io/vijayptiwari/charts/eventore --version 0.2.1
```

Review deployment values before exposing the installation: the default mode is Dev, API authentication is disabled until configured, and ingress requires environment-specific settings. The website's deployment guide provides an authenticated single-replica example.

### Versioned artifacts

- Backend: `ghcr.io/vijayptiwari/eventore-backend:0.2.1-<provider>`; for example `0.2.1-kafka`, `0.2.1-mqtt`, `0.2.1-kafka-kinesis`, or `0.2.1-all`.
- Frontend: `ghcr.io/vijayptiwari/eventore-frontend:0.2.1`.
- MCP: `ghcr.io/vijayptiwari/eventore-mcp:0.2.1`.
- Helm: `eventore` and `eventore-mcp`, version `0.2.1`. Chart archives and SHA-256 checksums are attached.

Provider-qualified backend tags prevent different provider bundles from overwriting one another. Chart image defaults follow the chart's application version. Development builds use separate Git-SHA image tags and prerelease chart versions.

### Adoption notes

Back up connection profiles and review deployment values before upgrading. Provider capabilities differ; choose between static API-token authentication or enterprise OIDC/JWT Bearer tokens. For multi-replica Kubernetes clusters, enable Redis cluster fan-out mode (`eventore.cluster.mode: REDIS`) and externalized JDBC or CRD connection stores for stateless active-active scaling. Cloud SDK paths are covered by mocked tests; validate cloud deployments in your own environment.

This release is published only after backend, broker integration, frontend, live browser, MCP, version-consistency, image, and chart publishing jobs succeed.

Version 0.2.0 was an unsuccessful release attempt and is not an adoption target. Its tag is retained for traceability; use 0.2.1.

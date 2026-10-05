# EventOre 0.3.0 — release preparation

0.3.0 is the development target and is not yet published. Install the latest verified release, [0.2.1](https://github.com/vijayptiwari/eventore/releases/tag/v0.2.1), until a successful release pipeline publishes new artifacts.

## Changes since 0.2.1

- HS256 JWT identity validation and claim extraction; authentication now requires signature verification and expiration, with configured issuer/audience checks.
- In-memory workspace catalog and console selector.
- Local subscription distribution interface and node status API.
- MCP incident triage tool and prompt using broker, lag and DLQ diagnostics.
- Replication routing and error handling fixes, empty-connection UX, schema and replay guides.

## Implementation limits

- JWT support uses a shared HS256 secret. OIDC discovery, JWKS, OAuth2 login and enterprise SSO are not implemented.
- Workspaces are metadata, not tenant isolation. Broker operations do not enforce the extracted user roles or workspace selection.
- The Redis bus is a scaffold without a network transport. Subscriptions remain process-local; use a single backend replica. Shared connection persistence does not supply active-active streaming.
- Triage uses fixed heuristics and can omit failed or unsupported checks. It provides investigation hints, not autonomous remediation or a complete health verdict.

## Release status and versioning

The root VERSION file tracks the development target across backend, frontend, MCP, OpenAPI and Helm manifests. Published website metadata is synchronized separately from a GitHub Release with uploaded chart archives and SHA256SUMS. No 0.3.0 download or install target is advertised before publication.

Before publishing, run backend, frontend, MCP, browser, version-consistency and packaging checks. Review the implementation limits above before adoption. Version 0.2.0 was an unsuccessful packaging attempt; 0.2.1 remains the published adoption target.

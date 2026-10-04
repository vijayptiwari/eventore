# EventOre documentation site

Guide-to-guide navigation keeps the site shell mounted and fetches only the next article, with a short content fade. Browser history, direct links, and full-navigation fallback remain supported. Guide styles load in the HTML head to avoid an unstyled flash on initial load.

The site and product header share a graphite-and-blue identity, a converging-stream symbol, and the EventOre wordmark. Landing-page copy centers on operator workflows and links directly to setup, capability differences, and deployment guides. Shared brand styles live in `css/brand.css`; the illustrative broker selector and command-copy behavior live in `js/home.js`. The preview uses sample data and does not connect to a broker. See `BRAND.md` for maintenance rules.

Product guide and diagrams in this folder for **GitHub Pages** (`Settings → Pages → /docs`).

## GitHub Pages setup

1. **Settings → Pages** → Branch: `main`, Folder: **`/docs`**
2. Ensure `docs/.nojekyll` exists (disables Jekyll; serves static HTML as-is)
3. Site URL: `https://<org>.github.io/<repo>/`

## Local preview

```bash
npx serve docs -l 3456
```

Open **http://localhost:3456/index.html** (or `/` after `serve.json` loads) — not the folder listing. If you see “Index of docs”, restart the command above from the repo root.

Prefer **`.html` URLs** (e.g. `http://localhost:3456/guide/pulsar.html`). Shortcuts `/kafka` and `/guide/pulsar` redirect via `serve.json` (local) and `404.html` (GitHub Pages). Guide pages load CSS/JS through an inline bootstrap so trailing-slash paths still style correctly when the host serves the file.

## Documentation map (all pages)

The primary **Product** navigation opens `product.html`, with `releases.html` for version and feature history and `roadmap.html` for upcoming directions. Protocols remain in Documentation. Edit `product/updates.json` for curated development milestones and roadmap statuses, then run `node docs/scripts/build-product.mjs` before the SEO generator. Keep commit dates distinct from release dates; only add a numbered stable release when backed by release evidence. Generated pages are committed for static previews and rebuilt in the publishing workflow.

Published release metadata comes from `node docs/scripts/sync-release.mjs`, which verifies the GitHub Release and its chart/checksum assets before creating `product/release.json`. The website refreshes after a successful release workflow. See `RELEASING.md` for the versioning and publication procedure.

Start with `guide/examples.html` for a complete local MQTT lab, then `guide/user-guide.html` for the UI walkthrough. `guide/troubleshooting.html` covers common failures. Downloadable tutorial files live in `examples/`; Compose build paths assume the repository checkout. Configuration and deployment examples were checked against the Spring properties, API contract, and Helm templates. The Helm example uses one replica and a PVC; it is not an HA recipe. The diagrams in `assets/diagrams/operator-workflow.svg`, `deployment-routing.svg`, and `configuration-layers.svg` have accessible text descriptions.

| Page | Path |
|------|------|
| Landing | `index.html` |
| Guide hub | `guide/index.html` |
| Getting started | `guide/getting-started.html` |
| Architecture | `guide/architecture.html` |
| Control & data plane | `guide/control-data-plane.html` |
| Configuration | `guide/configuration.html` |
| Connections | `guide/connections.html` |
| All platforms (comparison) | `guide/stream-platforms.html` |
| **Kafka** | `guide/kafka.html` |
| **Pulsar** | `guide/pulsar.html` |
| **RabbitMQ** | `guide/rabbitmq.html` |
| **MQTT** | `guide/mqtt.html` |
| **JMS** | `guide/jms.html` |
| **AWS Kinesis** | `guide/kinesis.html` |
| **GCP Pub/Sub** | `guide/gcp-pubsub.html` |
| **Azure Service Bus** | `guide/azure-service-bus.html` |
| Live streaming | `guide/streaming.html` |
| Deployment (GHCR images & OCI Helm) | `guide/deployment.html` (#published-artifacts) |
| MCP for AI agents | `guide/mcp.html` |
| Local development (contributors) | `guide/local-development.html` |

**Maintainer docs (Markdown, repo root):** `docs/TESTING.md`, `docs/HA.md`, `docs/REQUIREMENTS.md`, `docs/EPICS-WAVE3.md`, `CHANGELOG.md`.

**About** (not in the product guide): [`about.html`](about.html) — tabs for About EventOre, About the developer, and How to contribute. Linked from the home page and main nav.

## Path resolution

- Guide pages use an **inline bootstrap** (in each `guide/*.html` head) to set `__EVENTORE_BASE__` and load `js/base-path.js` + `js/guide-init.js` from the docs root (fixes `/guide/page/` where `../css` breaks).
- `js/guide-init.js` injects `css/site.css` and `js/site.js` from that root.
- Sidebar links use root-absolute paths via `js/site.js` so they work from any guide URL shape.

## SEO

The docs site is optimized for search engines and social previews (LinkedIn, X, Slack).

| Asset | Purpose |
|-------|---------|
| `seo/site.json` | Canonical origin (`https://vijayptiwari.github.io/eventore/`) |
| `seo/pages.json` | Per-page title, description, keywords |
| `scripts/inject-seo.mjs` | Regenerates `<!-- eventore-seo -->` blocks and `sitemap.xml` |
| `robots.txt` | Crawler rules + sitemap URL |
| `sitemap.xml` | All public pages (regenerated by inject-seo) |
| `assets/og-card.png` | Default Open Graph / Twitter image (1200×630); editable SVG source alongside it |

After changing copy in `seo/pages.json`, refresh HTML and sitemap:

```bash
node docs/scripts/inject-seo.mjs
node docs/scripts/check-links.mjs
node docs/scripts/check-seo.mjs
```

CI runs the same step in `.github/workflows/docs-pages.yml` before deploy.

**Search Console:** add property `https://vijayptiwari.github.io/eventore/` and submit `sitemap.xml` after deployment. Rankings and indexing depend on search engines; metadata does not guarantee placement. The homepage canonical is the site root. Guide titles, canonical links, social metadata, and structured data are regenerated from `seo/pages.json`. Essential header navigation is emitted in HTML for use without JavaScript. GitHub Pages project sites cannot control the host-root `robots.txt`; submit the project sitemap directly in Search Console.

Update `seo/site.json` if you use a custom domain or fork under a different GitHub Pages path.

To regenerate the social preview after editing `assets/og-card.svg`, install the frontend dependencies and Playwright Chromium, then run `node docs/scripts/render-social.mjs`. Inspect the PNG before publishing. System fonts and an SVG symbol keep the website free of third-party font and image requests.

## Structure

```
docs/
  index.html
  .nojekyll
  robots.txt
  sitemap.xml
  seo/site.json, pages.json
  scripts/inject-seo.mjs
  css/site.css
  js/base-path.js, site.js
  assets/logo*.svg, og-card.svg, diagrams/*.svg
  guide/*.html
```

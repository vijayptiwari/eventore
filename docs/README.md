# Eventore documentation site

Product guide and diagrams in this folder for **GitHub Pages** (`Settings → Pages → /docs`).

## GitHub Pages setup

1. **Settings → Pages** → Branch: `main`, Folder: **`/docs`**
2. Ensure `docs/.nojekyll` exists (disables Jekyll; serves static HTML as-is)
3. Site URL: `https://<org>.github.io/<repo>/`

## Local preview

```bash
npx serve docs
```

## Documentation map (all pages)

| Page | Path |
|------|------|
| Landing | `index.html` |
| Guide hub | `guide/index.html` |
| Getting started | `guide/getting-started.html` |
| Architecture | `guide/architecture.html` |
| Control & data plane | `guide/control-data-plane.html` |
| Configuration | `guide/configuration.html` |
| Connections | `guide/connections.html` |
| Stream platforms | `guide/stream-platforms.html` |
| Streaming & live view | `guide/streaming.html` |
| Inspection | `guide/inspection.html` |
| Kafka admin | `guide/kafka-admin.html` |
| Deployment (GHCR images & OCI Helm) | `guide/deployment.html` (#published-artifacts) |
| MCP for AI agents | `guide/mcp.html` |

## Path resolution

- Guide pages use **document-relative** links (`deployment.html`, `../assets/...`).
- `js/base-path.js` sets `__EVENTORE_BASE__` for the header/footer only (no `<base>` tag — that broke guide links).
- Sidebar links use absolute paths via `js/site.js` so they work from any guide page.

## SEO

The docs site is optimized for search engines and social previews (LinkedIn, X, Slack).

| Asset | Purpose |
|-------|---------|
| `seo/site.json` | Canonical origin (`https://vijayptiwari.github.io/eventore/`) |
| `seo/pages.json` | Per-page title, description, keywords |
| `scripts/inject-seo.mjs` | Regenerates `<!-- eventore-seo -->` blocks and `sitemap.xml` |
| `robots.txt` | Crawler rules + sitemap URL |
| `sitemap.xml` | All 13 public pages |
| `assets/og-card.svg` | Default Open Graph / Twitter image (1200×630) |

After changing copy in `seo/pages.json`, refresh HTML and sitemap:

```bash
node docs/scripts/inject-seo.mjs
```

CI runs the same step in `.github/workflows/docs-pages.yml` before deploy.

**Search Console:** add property `https://vijayptiwari.github.io/eventore/` and submit `sitemap.xml`. For best LinkedIn previews, replace `og-card.svg` with a PNG at the same path (many networks ignore SVG).

Update `seo/site.json` if you use a custom domain or fork under a different GitHub Pages path.

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

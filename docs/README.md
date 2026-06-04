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
| Deployment | `guide/deployment.html` |

## Path resolution

- Guide pages use **document-relative** links (`deployment.html`, `../assets/...`).
- `js/base-path.js` sets `__EVENTORE_BASE__` for the header/footer only (no `<base>` tag — that broke guide links).
- Sidebar links use absolute paths via `js/site.js` so they work from any guide page.

## Structure

```
docs/
  index.html
  .nojekyll
  css/site.css
  js/base-path.js, site.js
  assets/logo*.svg, diagrams/*.svg
  guide/*.html
```

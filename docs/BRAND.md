# EventOre brand and content maintenance

EventOre is a self-hosted messaging and streaming console. Lead with what an operator can do: inspect messages, understand supported broker state, and work across protocols from one workspace.

Use **EventOre** in customer-facing text. Keep technical identifiers, image names, paths, and configuration keys unchanged. The signature line is **Follow the message. Find the answer.** Pair it with a plain description of the product, especially in search titles and introductions.

## Visual identity

The symbol represents multiple event streams converging into one point of inspection. Its small diamond endpoint gives the mark a recognizable silhouette. Keep its proportions and clear space; do not stretch it. `logo-light.svg` is for dark backgrounds, `logo.svg` for light backgrounds, and `logo-mark.svg` for compact surfaces. Keep copies in `docs/assets` and `frontend/public/assets` identical.

Use graphite `#0c111b`, cool white `#edf3ff`, and electric blue `#75a7ff`. Blue identifies primary actions and navigation. Operational success, warning, and error colors in the application retain their meanings. Body text uses system fonts; headings use clean sans-serif typography and technical labels use monospace. No remote fonts are required. Prefer spacious layouts, thin borders, legible text, and quiet motion. Respect reduced-motion preferences.

## Product claims

Eight protocols share a workspace, but their capabilities are not identical. Link to `guide/stream-platforms.html` when discussing feature coverage. Call out Kafka specifically for partition lag. Describe Admin, Dev, and ReadOnly as deployment modes, not as an identity or role-management system. Explain that the Docker stack starts EventOre and needs an existing broker. Label illustrative data as sample data.

Do not add unverified customer counts, performance guarantees, compliance certifications, uptime claims, or license terms. The repository README currently calls Apache-2.0 “suggested”; a license file was not present during this update, so the site no longer asserts that license.

## Search and publishing

Edit titles and descriptions in `seo/pages.json`, then run the metadata generator, link checker, and SEO checker documented in `README.md`. Keep descriptions specific to the page. Use visible, accurate content to support structured data. Do not use keyword repetition or unsupported ratings. The SVG social card is editable source; regenerate its 1200×630 PNG when the design changes and inspect the PNG before publishing.

The approach follows [Google’s SEO Starter Guide](https://developers.google.com/search/docs/fundamentals/seo-starter-guide): descriptive titles, useful content, crawlable links, and consistent URLs. After deployment, inspect representative URLs and submit the sitemap in Search Console. Track indexing, search impressions, clicks, and real-user performance before claiming SEO gains.

#!/usr/bin/env node
/**
 * Injects static SEO tags into docs HTML and regenerates sitemap.xml.
 * Run from repo root: node docs/scripts/inject-seo.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const docsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const site = JSON.parse(fs.readFileSync(path.join(docsDir, 'seo', 'site.json'), 'utf8'));
const { pages } = JSON.parse(fs.readFileSync(path.join(docsDir, 'seo', 'pages.json'), 'utf8'));

const baseUrl = site.origin.replace(/\/$/, '') + site.basePath.replace(/\/?$/, '/');

function absUrl(relativePath) {
  return baseUrl + String(relativePath).replace(/^\//, '');
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;');
}

function homeJsonLd() {
  const siteUrl = absUrl('index.html');
  const graph = [
    {
      '@type': 'WebSite',
      '@id': siteUrl + '#website',
      name: site.siteName,
      url: siteUrl,
      description:
        'Unified console for Kafka, MQTT, RabbitMQ, Pulsar, JMS, Kinesis, GCP Pub/Sub, and Azure Service Bus.',
      publisher: { '@type': 'Organization', name: site.siteName },
    },
    {
      '@type': 'SoftwareApplication',
      '@id': site.repoUrl + '#software',
      name: site.siteName,
      applicationCategory: 'DeveloperApplication',
      operatingSystem: 'Linux, Windows, macOS',
      description:
        'Open-source multi-protocol streaming console with live view, inspection, Helm deployment, and optional MCP for AI agents.',
      url: site.repoUrl,
      downloadUrl: site.repoUrl,
      softwareHelp: absUrl('guide/index.html'),
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
    },
  ];
  return (
    '  <script type="application/ld+json">' +
    JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }) +
    '</script>'
  );
}

function buildSeoBlock(page) {
  const canonical = absUrl(page.path);
  const image = absUrl(page.image || site.defaultOgImage);
  const lines = [];
  if (page.jsonLd === 'home' && Array.isArray(site.siteVerification)) {
    for (const tag of site.siteVerification) {
      lines.push(
        '  <meta name="' +
          escapeHtml(tag.name) +
          '" content="' +
          escapeHtml(tag.content) +
          '"/>'
      );
    }
  }
  lines.push(
    '  <meta name="description" content="' + escapeHtml(page.description) + '"/>',
    '  <meta name="robots" content="index, follow, max-image-preview:large"/>'
  );
  if (page.keywords) {
    lines.push('  <meta name="keywords" content="' + escapeHtml(page.keywords) + '"/>');
  }
  lines.push(
    '  <link rel="canonical" href="' + escapeHtml(canonical) + '"/>',
    '  <meta property="og:type" content="' + escapeHtml(page.ogType || 'article') + '"/>',
    '  <meta property="og:site_name" content="' + escapeHtml(site.siteName) + '"/>',
    '  <meta property="og:title" content="' + escapeHtml(page.title) + '"/>',
    '  <meta property="og:description" content="' + escapeHtml(page.description) + '"/>',
    '  <meta property="og:url" content="' + escapeHtml(canonical) + '"/>',
    '  <meta property="og:image" content="' + escapeHtml(image) + '"/>',
    '  <meta property="og:image:alt" content="Eventore — multi-stream messaging console"/>',
    '  <meta property="og:locale" content="en_US"/>',
    '  <meta name="twitter:card" content="summary_large_image"/>',
    '  <meta name="twitter:title" content="' + escapeHtml(page.title) + '"/>',
    '  <meta name="twitter:description" content="' + escapeHtml(page.description) + '"/>',
    '  <meta name="twitter:image" content="' + escapeHtml(image) + '"/>'
  );
  if (site.twitterSite) {
    lines.push('  <meta name="twitter:site" content="' + escapeHtml(site.twitterSite) + '"/>');
  }
  if (page.jsonLd === 'home') {
    lines.push(homeJsonLd());
  }
  return lines.join('\n');
}

const SEO_START = '  <!-- eventore-seo -->';
const SEO_END = '  <!-- /eventore-seo -->';

function injectPage(page) {
  const filePath = path.join(docsDir, page.file);
  if (!fs.existsSync(filePath)) {
    throw new Error('Missing HTML file: ' + page.file);
  }
  let html = fs.readFileSync(filePath, 'utf8');
  const block = SEO_START + '\n' + buildSeoBlock(page) + '\n' + SEO_END;

  if (html.includes(SEO_START)) {
    html = html.replace(
      new RegExp(SEO_START.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + SEO_END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
      block
    );
  } else {
    const titleMatch = html.match(/<title>[^<]+<\/title>/);
    if (!titleMatch) throw new Error('No <title> in ' + page.file);
    html = html.replace(titleMatch[0], titleMatch[0] + '\n' + block);
  }

  html = html.replace(
    /\s*<meta name="description" content="[^"]*"\/>/,
    (m, offset) => (html.indexOf(SEO_START) >= 0 && offset < html.indexOf(SEO_START) ? '' : m)
  );
  if (html.match(/<meta name="description"/g)?.length > 1) {
    html = html.replace(/(<title>[^<]+<\/title>\s*)\s*<meta name="description" content="[^"]*"\/>/, '$1');
  }

  fs.writeFileSync(filePath, html, 'utf8');
  console.log('SEO:', page.file);
}

function writeSitemap() {
  const urls = pages
    .map((p) => {
      const loc = absUrl(p.path);
      const priority = p.path === 'index.html' ? '1.0' : p.path === 'guide/index.html' ? '0.9' : '0.8';
      const changefreq = p.path === 'index.html' ? 'weekly' : 'monthly';
      return (
        '  <url>\n' +
        '    <loc>' +
        escapeXml(loc) +
        '</loc>\n' +
        '    <changefreq>' +
        changefreq +
        '</changefreq>\n' +
        '    <priority>' +
        priority +
        '</priority>\n' +
        '  </url>'
      );
    })
    .join('\n');

  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    urls +
    '\n</urlset>\n';
  fs.writeFileSync(path.join(docsDir, 'sitemap.xml'), xml, 'utf8');
  console.log('Wrote sitemap.xml (' + pages.length + ' URLs)');
}

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function writeRobots() {
  const body =
    'User-agent: *\n' +
    'Allow: /\n\n' +
    'Sitemap: ' +
    absUrl('sitemap.xml') +
    '\n';
  fs.writeFileSync(path.join(docsDir, 'robots.txt'), body, 'utf8');
  console.log('Wrote robots.txt');
}

for (const page of pages) {
  injectPage(page);
}
writeSitemap();
writeRobots();
console.log('Done. Base URL:', baseUrl);

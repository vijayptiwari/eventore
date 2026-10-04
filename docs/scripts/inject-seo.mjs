#!/usr/bin/env node
/**
 * Injects static SEO tags into docs HTML and regenerates sitemap.xml and robots.txt.
 * Includes JSON-LD structured data (WebSite, SoftwareApplication, FAQPage, BreadcrumbList, TechArticle).
 * Run from repo root: node docs/scripts/inject-seo.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const docsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const site = JSON.parse(fs.readFileSync(path.join(docsDir, 'seo', 'site.json'), 'utf8'));
const { pages } = JSON.parse(fs.readFileSync(path.join(docsDir, 'seo', 'pages.json'), 'utf8'));
const releaseFile = path.join(docsDir, 'product', 'release.json');
const publishedRelease = fs.existsSync(releaseFile) ? JSON.parse(fs.readFileSync(releaseFile, 'utf8')) : null;

const baseUrl = site.origin.replace(/\/$/, '') + site.basePath.replace(/\/?$/, '/');

function absUrl(relativePath) {
  const normalized = String(relativePath).replace(/^\//, '');
  return baseUrl + (normalized === 'index.html' ? '' : normalized);
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
        'Unified open-source console for Kafka, RabbitMQ, Pulsar, MQTT, JMS, Kinesis, GCP Pub/Sub, and Azure Service Bus.',
      publisher: { '@type': 'Organization', name: site.siteName },
    },
    {
      '@type': 'SoftwareApplication',
      '@id': site.repoUrl + '#software',
      name: site.siteName,
      applicationCategory: 'DeveloperApplication',
      softwareVersion: publishedRelease?.version,
      operatingSystem: 'Linux, Windows, macOS, Docker, Kubernetes',
      description:
        'Open-source multi-protocol streaming console with cross-broker replication, PII data masking, OpenTelemetry tracing, DLQ redrive, partition lag heatmap, and MCP AI agent tools.',
      url: siteUrl,
      sameAs: site.repoUrl,
      downloadUrl: publishedRelease?.url || site.repoUrl + '/releases',
      releaseNotes: absUrl('releases.html'),
      softwareHelp: absUrl('guide/index.html'),
      featureList: [
        'Multi-protocol federated support for 8 streaming systems (Kafka, RabbitMQ, Pulsar, MQTT, JMS, AWS Kinesis, GCP Pub/Sub, Azure Service Bus)',
        'Cross-Broker Data Replication & Bridging with loop prevention and dry-run tester',
        'Configurable field masking and payload preview tools',
        'OpenTelemetry Distributed Tracing with W3C TraceContext and APM deep links',
        'Visual Partition Lag Heatmap and automated lag skew detection',
        'Dead-Letter Queue (DLQ) Inspector and rate-limited message redrive engine',
        'Externalized Connection Stores: PostgreSQL JDBC with optimistic locking and Kubernetes CRDs',
        'Model Context Protocol (MCP) server for Cursor, Claude, and AI agent integration'
      ],
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
    },

  ];
  return (
    '  <script type="application/ld+json">' +
    JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }) +
    '</script>'
  );
}

function guideJsonLd(page) {
  const pageUrl = absUrl(page.path);
  const homeUrl = absUrl('index.html');
  const hubUrl = absUrl('guide/index.html');
  const graph = [
    {
      '@type': 'BreadcrumbList',
      '@id': pageUrl + '#breadcrumb',
      itemListElement: [
        {
          '@type': 'ListItem',
          position: 1,
          name: 'Home',
          item: homeUrl
        },
        {
          '@type': 'ListItem',
          position: 2,
          name: 'Documentation',
          item: hubUrl
        },
        {
          '@type': 'ListItem',
          position: 3,
          name: page.title.replace(/\s*—\s*EventOre\s*$/, ''),
          item: pageUrl
        }
      ]
    },
    {
      '@type': 'TechArticle',
      '@id': pageUrl + '#article',
      headline: page.title,
      description: page.description,
      url: pageUrl,
      mainEntityOfPage: pageUrl,
      publisher: { '@type': 'Organization', name: site.siteName },
      author: { '@type': 'Person', name: 'Vijay Prakash Tiwari' }
    }
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
  lines.push(
    '  <link rel="canonical" href="' + escapeHtml(canonical) + '"/>',
    '  <link rel="sitemap" type="application/xml" href="' + escapeHtml(absUrl('sitemap.xml')) + '"/>',
    '  <meta property="og:type" content="' + escapeHtml(page.ogType || 'article') + '"/>',
    '  <meta property="og:site_name" content="' + escapeHtml(site.siteName) + '"/>',
    '  <meta property="og:title" content="' + escapeHtml(page.title) + '"/>',
    '  <meta property="og:description" content="' + escapeHtml(page.description) + '"/>',
    '  <meta property="og:url" content="' + escapeHtml(canonical) + '"/>',
    '  <meta property="og:image" content="' + escapeHtml(image) + '"/>',
    '  <meta property="og:image:width" content="1200"/>',
    '  <meta property="og:image:height" content="630"/>',
    '  <meta property="og:image:type" content="image/png"/>',
    '  <meta name="theme-color" content="#0c111b"/>',
    '  <meta property="og:image:alt" content="EventOre — unified multi-stream messaging console"/>',
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
  } else if (page.path.startsWith('guide/') && page.path !== 'guide/index.html') {
    lines.push(guideJsonLd(page));
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
  html = html.replace(/<title>[^<]*<\/title>/, '<title>' + escapeHtml(page.title) + '</title>');
  // Keep essential navigation usable and crawlable before JavaScript runs.
  const root = page.file.startsWith('guide/') ? '../' : './';
  const header = '<div class="site-header-inner"><a class="site-logo" href="' + root + 'index.html" aria-label="EventOre home"><img src="' + root + 'assets/logo-light.svg" alt="EventOre" width="190" height="40"/></a><nav class="site-nav" aria-label="Primary"><a href="' + root + 'product.html">Product</a><a href="' + root + 'guide/index.html">Documentation</a><a href="' + root + 'about.html">About</a><a href="' + site.repoUrl + '">GitHub</a><a class="site-nav-cta" href="' + root + 'guide/getting-started.html">Get started</a></nav></div>';
  html = html.replace(/(<header\b[^>]*id="site-header"[^>]*>)[\s\S]*?(<\/header>)/, '$1' + header + '$2');
  if (!html.includes('class="skip-link"')) {
    html = html.replace(/<body>/, '<body>\n<a class="skip-link" href="#main">Skip to content</a>');
  }
  html = html.replace(/<main\b(?![^>]*\bid=)([^>]*)>/, '<main id="main"$1>');
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

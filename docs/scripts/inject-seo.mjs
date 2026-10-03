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
        'Unified open-source console for Kafka, RabbitMQ, Pulsar, MQTT, JMS, Kinesis, GCP Pub/Sub, and Azure Service Bus.',
      publisher: { '@type': 'Organization', name: site.siteName },
    },
    {
      '@type': 'SoftwareApplication',
      '@id': site.repoUrl + '#software',
      name: site.siteName,
      applicationCategory: 'DeveloperApplication',
      operatingSystem: 'Linux, Windows, macOS, Docker, Kubernetes',
      description:
        'Open-source multi-protocol streaming console with cross-broker replication, PII data masking, OpenTelemetry tracing, DLQ redrive, partition lag heatmap, and MCP AI agent tools.',
      url: site.repoUrl,
      downloadUrl: site.repoUrl,
      softwareHelp: absUrl('guide/index.html'),
      license: 'https://www.apache.org/licenses/LICENSE-2.0',
      featureList: [
        'Multi-protocol federated support for 8 streaming systems (Kafka, RabbitMQ, Pulsar, MQTT, JMS, AWS Kinesis, GCP Pub/Sub, Azure Service Bus)',
        'Cross-Broker Data Replication & Bridging with loop prevention and dry-run tester',
        'Field-Level Data Masking & PII Redaction with Jackson AST and regex scanners',
        'OpenTelemetry Distributed Tracing with W3C TraceContext and APM deep links',
        'Visual Partition Lag Heatmap and automated lag skew detection',
        'Dead-Letter Queue (DLQ) Inspector and rate-limited message redrive engine',
        'Externalized Connection Stores: PostgreSQL JDBC with optimistic locking and Kubernetes CRDs',
        'Model Context Protocol (MCP) server for Cursor, Claude, and AI agent integration'
      ],
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
    },
    {
      '@type': 'FAQPage',
      '@id': siteUrl + '#faq',
      mainEntity: [
        {
          '@type': 'Question',
          name: 'What is Eventore?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Eventore is an enterprise-grade open-source multi-protocol messaging and streaming console that unifies Apache Kafka, RabbitMQ, Pulsar, MQTT, JMS, AWS Kinesis, GCP Pub/Sub, and Azure Service Bus into a single web UI and API.'
          }
        },
        {
          '@type': 'Question',
          name: 'How does Eventore compare to Kafka UI, AKHQ, or Conduktor?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Unlike single-protocol tools like Kafka UI or AKHQ which only support Kafka, Eventore supports 8 distinct messaging protocols simultaneously, enables cross-broker replication (e.g., Kafka to RabbitMQ), provides real-time PII data masking, OpenTelemetry distributed tracing, DLQ message redrive, and AI agent integration via Model Context Protocol (MCP).'
          }
        },
        {
          '@type': 'Question',
          name: 'How does cross-broker data replication work in Eventore?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Eventore\'s Replication Bridge Engine transfers messages between heterogeneous brokers (e.g. Kafka to RabbitMQ, or Kinesis to GCP Pub/Sub) using Java Virtual Threads, automatic loop prevention, header transformations, regex payload filters, and an interactive dry-run simulator.'
          }
        },
        {
          '@type': 'Question',
          name: 'Can Eventore mask sensitive PII and confidential fields in streaming messages?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Yes. Eventore\'s real-time DataMasker inspects JSON payloads using recursive AST parsing and regex patterns, supporting full redaction, partial masking (e.g. credit card trailing digits), and salted SHA-256 hashing to ensure GDPR, PCI-DSS, and HIPAA compliance.'
          }
        },
        {
          '@type': 'Question',
          name: 'Does Eventore support OpenTelemetry distributed tracing?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Yes. Eventore natively extracts and propagates W3C TraceContext traceparent headers across brokers, creating child bridge spans and providing one-click deep links directly into Jaeger, Zipkin, Datadog, and other APM tools.'
          }
        },
        {
          '@type': 'Question',
          name: 'How do AI agents interact with Eventore via Model Context Protocol (MCP)?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Eventore provides an official MCP server (eventore-mcp) enabling AI developer tools like Cursor, Claude Desktop, and Gemini to safely inspect topics, publish test messages, check consumer lag, and analyze cluster health via structured agent tools.'
          }
        }
      ]
    }
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
          name: page.title.replace(/\s*—\s*Eventore\s*$/, ''),
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
    '  <meta property="og:image:alt" content="Eventore — unified multi-stream messaging console"/>',
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

function initializeEventOreSite() {
  var base = window.__EVENTORE_BASE__ || '/';
  var GITHUB = 'https://github.com/vijayptiwari/eventore';

  function asset(path) {
    return base + String(path).replace(/^\//, '');
  }

  function guideHref(file) {
    return asset('guide/' + file);
  }

  var path = location.pathname.replace(/\\/g, '/');

  function inGuideSection() {
    return /\/guide(?:\/|$)/.test(path);
  }

  function currentGuideFile() {
    if (!inGuideSection()) return null;
    if (/\/guide\/?$/.test(path)) return 'index.html';
    var m = path.match(/\/guide\/([^/?#]+?)\/?$/);
    if (!m) return null;
    var name = m[1];
    return /\.html$/i.test(name) ? name : name + '.html';
  }

  function isGuideActive(href) {
    return currentGuideFile() === href;
  }

  function isHomeActive() {
    return !inGuideSection() && (path === base || /\/index\.html?$/i.test(path));
  }

  function isAboutActive() {
    return /\/about\.html?$/i.test(path);
  }

  var navItems = [
    { href: asset('product.html'), label: 'Product', active: function () { return /\/(product|releases|roadmap)\.html$/i.test(path); } },
    { href: asset('guide/index.html'), label: 'Documentation', active: inGuideSection },
    { href: asset('about.html'), label: 'About', active: isAboutActive },
    { href: GITHUB, label: 'GitHub', external: true },
  ];

  var capabilityLinks = [
    { href: 'replication-bridging.html', label: 'Cross-broker replication' },
    { href: 'data-masking.html', label: 'PII data masking' },
    { href: 'schema-registry.html', label: 'Schema registry & Avro' },
    { href: 'replay.html', label: 'Time-travel message replay' },
    { href: 'tracing.html', label: 'Distributed tracing' },
    { href: 'topology-lag.html', label: 'Lag heatmap & topology' },
    { href: 'dlq.html', label: 'DLQ & message redrive' },
    { href: 'connection-stores.html', label: 'Externalized stores' },
  ];

  var guideCoreLinks = [
    { href: 'index.html', label: 'Overview' },
    { href: 'getting-started.html', label: 'Getting started' },
    { href: 'examples.html', label: 'Examples & local lab' },
    { href: 'product-tour.html', label: 'Two-minute tour' },
    { href: 'kafka-rabbitmq-tutorial.html', label: 'Kafka → RabbitMQ lab' },
    { href: 'user-guide.html', label: 'User guide' },
    { href: 'troubleshooting.html', label: 'Troubleshooting' },
    { href: 'architecture.html', label: 'Architecture' },
    { href: 'control-data-plane.html', label: 'Control & data plane' },
    { href: 'configuration.html', label: 'Configuration' },
    { href: 'connections.html', label: 'Connections' },
    { href: 'stream-platforms.html', label: 'All platforms' },
    { href: 'streaming.html', label: 'Live streaming' },
    { href: 'deployment.html', label: 'Deployment' },
    { href: 'mcp.html', label: 'MCP for agents' },
    { href: 'local-development.html', label: 'Local development' },
  ];

  var streamGuideLinks = [
    { href: 'kafka.html', label: 'Kafka' },
    { href: 'pulsar.html', label: 'Pulsar' },
    { href: 'rabbitmq.html', label: 'RabbitMQ' },
    { href: 'mqtt.html', label: 'MQTT' },
    { href: 'jms.html', label: 'JMS' },
    { href: 'kinesis.html', label: 'AWS Kinesis' },
    { href: 'gcp-pubsub.html', label: 'GCP Pub/Sub' },
    { href: 'azure-service-bus.html', label: 'Azure Service Bus' },
  ];

  var footerDocLinks = guideCoreLinks.filter(function (l) {
    return l.href !== 'index.html';
  });

  function isStreamGuide(file) {
    return streamGuideLinks.some(function (s) {
      return s.href === file;
    });
  }

  function isCapabilityGuide(file) {
    return capabilityLinks.some(function (c) {
      return c.href === file;
    });
  }

  function linkAttrs(external) {
    return external ? ' target="_blank" rel="noopener noreferrer"' : '';
  }

  function renderSidebarLinks(links) {
    return links
      .map(function (l) {
        return (
          '<li><a href="' +
          guideHref(l.href) +
          '" class="' +
          (isGuideActive(l.href) ? 'active' : '') +
          '">' +
          l.label +
          '</a></li>'
        );
      })
      .join('');
  }

  function injectGuidePager() {
    var file = currentGuideFile();
    if (!file || file === 'index.html') return;
    var links = isCapabilityGuide(file)
      ? capabilityLinks
      : isStreamGuide(file)
      ? streamGuideLinks
      : guideCoreLinks;
    var idx = links.findIndex(function (l) {
      return l.href === file;
    });
    if (idx < 0) return;
    var main = document.querySelector('.guide-layout main');
    if (!main) return;
    var prev = idx > 0 ? links[idx - 1] : null;
    var next = idx < links.length - 1 ? links[idx + 1] : null;
    var html = '<nav class="guide-pager" aria-label="Guide pagination">';
    if (prev) {
      html +=
        '<a class="guide-pager-prev" href="' +
        guideHref(prev.href) +
        '"><span class="guide-pager-label">Previous</span><span class="guide-pager-title">← ' +
        prev.label +
        '</span></a>';
    } else {
      html += '<span></span>';
    }
    if (next) {
      html +=
        '<a class="guide-pager-next" href="' +
        guideHref(next.href) +
        '"><span class="guide-pager-label">Next</span><span class="guide-pager-title">' +
        next.label +
        ' →</span></a>';
    }
    html += '</nav>';
    main.insertAdjacentHTML('beforeend', html);
  }

  function injectStreamToc(file) {
    if (!isStreamGuide(file) && !isCapabilityGuide(file)) return;
    var main = document.querySelector('.guide-layout main');
    if (!main || main.querySelector('.stream-onpage-nav')) return;
    var sections = main.querySelectorAll('h2[id]');
    if (!sections.length) return;
    var items = '';
    sections.forEach(function (h) {
      items += '<li><a href="#' + h.id + '">' + h.textContent + '</a></li>';
    });
    var nav =
      '<nav class="stream-onpage-nav" aria-label="On this page">' +
      '<p class="stream-onpage-title">On this page</p><ul>' +
      items +
      '</ul></nav>';
    main.insertAdjacentHTML('afterbegin', nav);
  }

  var header = document.getElementById('site-header');
  if (header) {
    var navHtml = navItems
      .map(function (n) {
        var active = n.active ? (n.active() ? 'active' : '') : '';
        return (
          '<a href="' +
          n.href +
          '" class="' +
          active +
          '"' +
          linkAttrs(n.external) +
          '>' +
          n.label +
          '</a>'
        );
      })
      .join('');
    navHtml +=
      '<a class="site-nav-cta" href="' +
      guideHref('getting-started.html') +
      '">Get started</a>';
    header.innerHTML =
      '<div class="site-header-inner">' +
      '<a class="site-logo" href="' +
      asset('index.html') +
      '" aria-label="EventOre home">' +
      '<img src="' +
      asset('assets/logo-light.svg') +
      '" alt="EventOre" width="200" height="36" decoding="async"/>' +
      '</a>' +
      '<nav class="site-nav" aria-label="Primary">' +
      navHtml +
      '</nav></div>';
  }

  var sidebar = document.getElementById('guide-sidebar-nav');
  if (sidebar && inGuideSection()) {
    sidebar.innerHTML =
      '<p class="guide-sidebar-title">Start &amp; operate</p><ul>' +
      renderSidebarLinks(guideCoreLinks) +
      '</ul><p class="guide-sidebar-title">Capabilities</p><ul class="guide-sidebar-capabilities">' +
      renderSidebarLinks(capabilityLinks) +
      '</ul><p class="guide-sidebar-title">Streams</p><ul class="guide-sidebar-streams">' +
      renderSidebarLinks(streamGuideLinks) +
      '</ul>';
    sidebar.insertAdjacentHTML('afterbegin', '<label class="guide-filter-label" for="guide-filter">Find a guide</label><input class="guide-filter" id="guide-filter" type="search" placeholder="Kafka, deployment, masking…" autocomplete="off"/><p class="guide-filter-status" role="status" hidden></p>');
    sidebar.querySelector('.guide-filter').addEventListener('input', function (event) {
      var query = event.target.value.trim().toLowerCase();
      var count = 0;
      sidebar.querySelectorAll('li').forEach(function (item) {
        item.hidden = item.textContent.toLowerCase().indexOf(query) === -1;
        if (!item.hidden) count++;
      });
      var status = sidebar.querySelector('.guide-filter-status');
      status.hidden = !query;
      status.textContent = count ? count + ' guides found' : 'No matching guides. Try a protocol or task.';
    });
  }

  var footer = document.getElementById('site-footer');
  if (footer) {
    var capList = capabilityLinks
      .map(function (l) {
        return (
          '<li><a href="' +
          guideHref(l.href) +
          '">' +
          l.label +
          '</a></li>'
        );
      })
      .join('');
    var docList = footerDocLinks
      .map(function (l) {
        return (
          '<li><a href="' +
          guideHref(l.href) +
          '">' +
          l.label +
          '</a></li>'
        );
      })
      .join('');
    var streamList = streamGuideLinks
      .map(function (l) {
        return (
          '<li><a href="' +
          guideHref(l.href) +
          '">' +
          l.label +
          '</a></li>'
        );
      })
      .join('');
    var resourceList = [
      { href: asset('product.html'), label: 'Product overview' },
      { href: asset('releases.html'), label: 'Release notes' },
      { href: asset('roadmap.html'), label: 'Roadmap' },
      { href: GITHUB, label: 'GitHub repository', external: true },
      { href: GITHUB + '/tree/main/deploy/helm', label: 'Helm charts', external: true },
      { href: guideHref('deployment.html'), label: 'Deployment options' },
      { href: guideHref('mcp.html'), label: 'MCP AI agents' },
    ]
      .map(function (l) {
        return (
          '<li><a href="' +
          l.href +
          '"' +
          linkAttrs(l.external) +
          '>' +
          l.label +
          '</a></li>'
        );
      })
      .join('');
    var year = new Date().getFullYear();
    footer.innerHTML =
      '<div class="site-footer-inner">' +
      '<div class="site-footer-top">' +
      '<div class="site-footer-brand">' +
      '<a href="' +
      asset('index.html') +
      '" aria-label="EventOre home">' +
      '<img src="' +
      asset('assets/logo-light.svg') +
      '" alt="EventOre" width="160" height="32" decoding="async"/>' +
      '</a>' +
      '<p class="site-footer-tagline">Unified multi-protocol streaming and messaging console for Kafka, RabbitMQ, Pulsar, MQTT, JMS, Kinesis, Pub/Sub, and Azure Service Bus.</p>' +
      '</div>' +
      '<div class="site-footer-col"><h4>Capabilities</h4><ul>' +
      capList +
      '</ul></div>' +
      '<div class="site-footer-col"><h4>Stream Guides</h4><ul>' +
      streamList +
      '</ul></div>' +
      '<div class="site-footer-col"><h4>Platform &amp; Ops</h4><ul>' +
      docList +
      '</ul></div>' +
      '<div class="site-footer-col"><h4>Resources</h4><ul>' +
      resourceList +
      '</ul></div>' +
      '</div>' +
      '<div class="site-footer-bottom">' +
      '<span>© ' +
      year +
      ' EventOre. Built in the open.</span>' +
      '<span><a href="' +
      guideHref('index.html') +
      '">Documentation</a> · <a href="' +
      asset('about.html') +
      '">About</a></span>' +
      '</div></div>';
  }

  function fixGuideRelativeLinks() {
    if (!inGuideSection()) return;
    document.querySelectorAll('.guide-layout a[href]').forEach(function (a) {
      var h = a.getAttribute('href');
      if (!h || h.indexOf('://') >= 0 || h.charAt(0) === '/' || h.charAt(0) === '#') return;
      if (h.indexOf('./') === 0 || h.indexOf('../') === 0) return;
      if (/^[a-z0-9][a-z0-9.-]*\.html/i.test(h)) {
        a.setAttribute('href', './' + h);
      }
    });
  }

  var file = currentGuideFile();
  fixGuideRelativeLinks();
  injectStreamToc(file);
  injectGuidePager();
  document.querySelectorAll('.guide-layout main pre').forEach(function (pre) {
    if (pre.parentElement.classList.contains('code-example')) return;
    var wrapper = document.createElement('div');
    wrapper.className = 'code-example';
    pre.replaceWith(wrapper);
    wrapper.appendChild(pre);
    var copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'code-copy';
    copy.textContent = 'Copy code';
    copy.setAttribute('aria-live', 'polite');
    wrapper.appendChild(copy);
    copy.addEventListener('click', async function () {
      try {
        await navigator.clipboard.writeText(pre.textContent);
        copy.textContent = 'Copied';
        setTimeout(function () { copy.textContent = 'Copy code'; }, 2000);
      } catch (error) {
        var range = document.createRange();
        range.selectNodeContents(pre);
        var selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        copy.textContent = 'Code selected';
      }
    });
  });
}
initializeEventOreSite();

// Keep the documentation shell mounted during guide-to-guide navigation.
(function () {
  if (!document.querySelector('.guide-layout')) return;
  var pending;
  var cache = new Map();
  function isGuide(url) {
    return url.origin === location.origin && url.pathname.indexOf((window.__EVENTORE_BASE__ || '/') + 'guide/') === 0 && /\.html$/.test(url.pathname);
  }
  async function load(url, signal) {
    if (cache.has(url.pathname)) return cache.get(url.pathname);
    var response = await fetch(url.href, { signal: signal });
    if (!response.ok) throw new Error('Guide unavailable');
    var parsed = new DOMParser().parseFromString(await response.text(), 'text/html');
    if (!parsed.querySelector('.guide-layout main')) throw new Error('Not a guide');
    cache.set(url.pathname, parsed);
    return parsed;
  }
  async function navigate(url, push) {
    if (pending) pending.abort();
    var controller = new AbortController();
    pending = controller;
    var main = document.querySelector('.guide-layout main');
    main.setAttribute('aria-busy', 'true');
    try {
      var parsed = await load(url, controller.signal);
      if (controller.signal.aborted) return;
      if (push) history.pushState(null, '', url.href);
      var next = document.importNode(parsed.querySelector('.guide-layout main'), true);
      main.replaceWith(next);
      document.title = parsed.title;
      document.querySelectorAll('head [data-guide-meta]').forEach(function (el) { el.remove(); });
      document.querySelectorAll('head meta[name="description"],head link[rel="canonical"],head meta[property^="og:"],head meta[name^="twitter:"],head script[type="application/ld+json"]').forEach(function (el) { el.remove(); });
      parsed.querySelectorAll('meta[name="description"],link[rel="canonical"],meta[property^="og:"],meta[name^="twitter:"],script[type="application/ld+json"]').forEach(function (el) {
        var clone = document.importNode(el, true); clone.setAttribute('data-guide-meta', ''); document.head.appendChild(clone);
      });
      initializeEventOreSite();
      window.scrollTo({ top: 0, behavior: 'instant' });
      if (url.hash) {
        var target = document.getElementById(decodeURIComponent(url.hash.slice(1)));
        if (target) target.scrollIntoView({ behavior: 'instant' });
      }
      next.setAttribute('tabindex', '-1');
      next.focus({ preventScroll: true });
      if (!matchMedia('(prefers-reduced-motion: reduce)').matches && next.animate) {
        next.animate([{ opacity: 0.65 }, { opacity: 1 }], { duration: 160, easing: 'ease-out' });
      }
    } catch (error) {
      if (error.name !== 'AbortError') location.assign(url.href);
    } finally {
      if (pending === controller) { pending = null; main.removeAttribute('aria-busy'); }
    }
  }
  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[href]');
    if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || link.target || link.hasAttribute('download')) return;
    var url = new URL(link.href);
    if (!isGuide(url) || (url.pathname === location.pathname && url.search === location.search)) return;
    event.preventDefault();
    navigate(url, true);
  });
  document.addEventListener('pointerover', function (event) {
    var link = event.target.closest('a[href]');
    if (!link || navigator.connection && navigator.connection.saveData) return;
    var url = new URL(link.href);
    if (isGuide(url) && !cache.has(url.pathname)) load(url).catch(function () {});
  });
  window.addEventListener('popstate', function () {
    var url = new URL(location.href);
    if (isGuide(url)) navigate(url, false); else location.reload();
  });
})();

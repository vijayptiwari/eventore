(function () {
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
    return path === '/' || /\/index\.html?$/i.test(path);
  }

  function isAboutActive() {
    return /\/about\.html?$/i.test(path);
  }

  var navItems = [
    { href: asset('index.html'), label: 'Home', active: isHomeActive },
    { href: asset('about.html'), label: 'About', active: isAboutActive },
    { href: asset('guide/index.html'), label: 'Documentation', active: inGuideSection },
    { href: GITHUB, label: 'GitHub', external: true },
  ];

  var guideCoreLinks = [
    { href: 'index.html', label: 'Overview' },
    { href: 'getting-started.html', label: 'Getting started' },
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
    var links = isStreamGuide(file) ? streamGuideLinks : guideCoreLinks;
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
    if (!isStreamGuide(file)) return;
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
      '" aria-label="Eventore home">' +
      '<img src="' +
      asset('assets/logo.svg') +
      '" alt="Eventore" width="200" height="36" decoding="async"/>' +
      '</a>' +
      '<nav class="site-nav" aria-label="Primary">' +
      navHtml +
      '</nav></div>';
  }

  var sidebar = document.getElementById('guide-sidebar-nav');
  if (sidebar && inGuideSection()) {
    sidebar.innerHTML =
      '<p class="guide-sidebar-title">Streams</p><ul class="guide-sidebar-streams">' +
      renderSidebarLinks(streamGuideLinks) +
      '</ul><p class="guide-sidebar-title">Platform</p><ul>' +
      renderSidebarLinks(guideCoreLinks) +
      '</ul>';
  }

  var footer = document.getElementById('site-footer');
  if (footer) {
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
      { href: GITHUB, label: 'GitHub', external: true },
      { href: GITHUB + '/tree/main/deploy/helm', label: 'Helm charts', external: true },
      { href: guideHref('deployment.html'), label: 'Deploy' },
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
      '" aria-label="Eventore home">' +
      '<img src="' +
      asset('assets/logo-light.svg') +
      '" alt="Eventore" width="160" height="32" decoding="async"/>' +
      '</a>' +
      '<p class="site-footer-tagline">One console for Kafka, MQTT, RabbitMQ, Pulsar, JMS, Kinesis, Pub/Sub, and Service Bus.</p>' +
      '</div>' +
      '<div class="site-footer-col"><h4>Platform</h4><ul>' +
      docList +
      '</ul></div>' +
      '<div class="site-footer-col site-footer-col--streams"><h4>Stream guides</h4><ul>' +
      streamList +
      '</ul></div>' +
      '<div class="site-footer-col"><h4>Resources</h4><ul>' +
      resourceList +
      '</ul></div>' +
      '</div>' +
      '<div class="site-footer-bottom">' +
      '<span>© ' +
      year +
      ' Eventore</span>' +
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
})();

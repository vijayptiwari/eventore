(function () {
  var base = window.__EVENTORE_BASE__ || '/';

  function asset(path) {
    return base + String(path).replace(/^\//, '');
  }

  function guideHref(file) {
    return asset('guide/' + file);
  }

  var navItems = [
    { href: asset('index.html'), label: 'Home', match: /index\.html$/ },
    { href: asset('guide/index.html'), label: 'Product guide', match: /\/guide\// },
  ];

  var guideLinks = [
    { href: 'index.html', label: 'Overview' },
    { href: 'getting-started.html', label: 'Getting started' },
    { href: 'architecture.html', label: 'Architecture' },
    { href: 'control-data-plane.html', label: 'Control & data plane' },
    { href: 'mcp.html', label: 'MCP for AI agents' },
    { href: 'configuration.html', label: 'Configuration' },
    { href: 'connections.html', label: 'Connections' },
    { href: 'stream-platforms.html', label: 'Stream platforms' },
    { href: 'streaming.html', label: 'Streaming & live view' },
    { href: 'inspection.html', label: 'Inspection' },
    { href: 'kafka-admin.html', label: 'Kafka admin' },
    { href: 'deployment.html', label: 'Deployment' },
  ];

  var guideOrder = guideLinks.map(function (l) {
    return l.href;
  });

  var path = window.location.pathname.replace(/\\/g, '/');

  function isActive(pattern) {
    if (pattern instanceof RegExp) return pattern.test(path);
    return path.endsWith(pattern);
  }

  function currentGuideFile() {
    var m = path.match(/\/guide\/([^/]+\.html)$/);
    return m ? m[1] : null;
  }

  function injectGuidePager() {
    var file = currentGuideFile();
    if (!file || file === 'index.html') return;
    var idx = guideOrder.indexOf(file);
    if (idx < 0) return;
    var main = document.querySelector('.guide-layout main');
    if (!main) return;
    var prev = idx > 0 ? guideLinks[idx - 1] : null;
    var next = idx < guideLinks.length - 1 ? guideLinks[idx + 1] : null;
    var html = '<nav class="guide-pager" aria-label="Guide pagination">';
    if (prev) {
      html +=
        '<a class="guide-pager-prev" href="' +
        prev.href +
        '">← ' +
        prev.label +
        '</a>';
    } else {
      html += '<span></span>';
    }
    if (next) {
      html +=
        '<a class="guide-pager-next" href="' +
        next.href +
        '">' +
        next.label +
        ' →</a>';
    }
    html += '</nav>';
    main.insertAdjacentHTML('beforeend', html);
  }

  var header = document.getElementById('site-header');
  if (header) {
    var navHtml = navItems
      .map(function (n) {
        return (
          '<a href="' +
          n.href +
          '" class="' +
          (isActive(n.match) ? 'active' : '') +
          '">' +
          n.label +
          '</a>'
        );
      })
      .join('');
    header.innerHTML =
      '<div class="site-header-inner">' +
      '<a class="site-logo" href="' +
      asset('index.html') +
      '" aria-label="Eventore home">' +
      '<img src="' +
      asset('assets/logo.svg') +
      '" alt="Eventore" width="220" height="44" decoding="async"/>' +
      '</a>' +
      '<nav class="site-nav" aria-label="Primary">' +
      navHtml +
      '</nav></div>';
  }

  var sidebar = document.getElementById('guide-sidebar-nav');
  if (sidebar) {
    var inGuide = path.includes('/guide/');
    sidebar.innerHTML = inGuide
      ? '<p class="guide-sidebar-title">Guide</p><ul>' +
        guideLinks
          .map(function (l) {
            var url = guideHref(l.href);
            return (
              '<li><a href="' +
              url +
              '" class="' +
              (path.endsWith('/guide/' + l.href) ? 'active' : '') +
              '">' +
              l.label +
              '</a></li>'
            );
          })
          .join('') +
        '</ul>'
      : '';
  }

  var footer = document.getElementById('site-footer');
  if (footer) {
    footer.innerHTML =
      '<div class="site-footer-inner">' +
      '<span><img src="' +
      asset('assets/logo-mark.svg') +
      '" alt="" width="28" height="28" decoding="async" class="footer-mark"/> Eventore — multi-stream messaging console</span>' +
      '<span><a href="' +
      asset('guide/index.html') +
      '">Product guide</a> · Open source</span></div>';
  }

  injectGuidePager();
})();

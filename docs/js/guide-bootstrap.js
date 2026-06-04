/**
 * First script on guide pages. Computes docs root from the URL (works at /guide/page/)
 * and loads base-path.js then guide-init.js from that root — never uses ../js.
 */
(function () {
  var path = location.pathname.replace(/\\/g, '/');

  function docsRoot(p) {
    var guideRoot = p.match(/^(.*)\/guide(?:\/|$)/);
    if (guideRoot) {
      var prefix = guideRoot[1];
      return (prefix ? prefix : '') + '/';
    }
    if (/\.[a-z0-9]+$/i.test(p)) {
      return p.replace(/\/[^/]*$/, '/') || '/';
    }
    if (p.endsWith('/')) return p;
    return p + '/';
  }

  window.__EVENTORE_BASE__ = docsRoot(path);

  function loadScript(src, onload) {
    var s = document.createElement('script');
    s.src = window.__EVENTORE_BASE__ + String(src).replace(/^\//, '');
    if (onload) s.onload = onload;
    document.head.appendChild(s);
  }

  loadScript('js/base-path.js', function () {
    loadScript('js/guide-init.js');
  });
})();

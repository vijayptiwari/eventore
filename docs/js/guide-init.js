/**
 * Resolves CSS, favicon, images, and site.js from docs root.
 * Required when the URL is /guide/page/ (trailing slash) — ../css breaks.
 */
(function () {
  var b = window.__EVENTORE_BASE__ || '/';

  function asset(path) {
    return b + String(path).replace(/^\//, '');
  }

  window.__EVENTORE_ASSET__ = asset;

  if (!document.getElementById('eventore-site-css')) {
    var css = document.createElement('link');
    css.id = 'eventore-site-css';
    css.rel = 'stylesheet';
    css.href = asset('css/site.css');
    document.head.appendChild(css);
  }

  var icon = document.querySelector('link[rel="icon"]');
  if (icon) {
    var href = icon.getAttribute('href') || '';
    if (href.indexOf('..') === 0 || href.indexOf('/') !== 0) {
      icon.href = asset('assets/logo-mark.svg');
    }
  }

  function fixRelativeUrls() {
    document.querySelectorAll('img[src^="../"]').forEach(function (el) {
      el.src = asset(el.getAttribute('src').slice(3));
    });
    document.querySelectorAll('script[src$="site.js"]').forEach(function (el) {
      var src = el.getAttribute('src') || '';
      if (src.indexOf('..') >= 0) el.src = asset('js/site.js');
    });
    document.querySelectorAll('script[src$="guide-init.js"]').forEach(function (el) {
      var src = el.getAttribute('src') || '';
      if (src.indexOf('..') >= 0) el.src = asset('js/guide-init.js');
    });
  }

  function loadSiteJs() {
    if (document.getElementById('eventore-site-js')) return;
    var s = document.createElement('script');
    s.id = 'eventore-site-js';
    s.src = asset('js/site.js');
    document.body.appendChild(s);
  }

  fixRelativeUrls();
  if (document.body) {
    loadSiteJs();
  } else {
    document.addEventListener('DOMContentLoaded', function () {
      fixRelativeUrls();
      loadSiteJs();
    });
  }
})();

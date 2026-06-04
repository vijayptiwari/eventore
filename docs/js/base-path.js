/**
 * Site root for GitHub Pages (/repo/) and local serve (/).
 * Does not use <base> — that breaks document-relative guide/*.html links.
 */
(function () {
  function siteBase() {
    var path = location.pathname.replace(/\\/g, '/');
    var guideIdx = path.indexOf('/guide/');
    if (guideIdx >= 0) return path.slice(0, guideIdx + 1);
    if (/\.[a-z0-9]+$/i.test(path)) return path.replace(/\/[^/]*$/, '/') || '/';
    return path.endsWith('/') ? path : path + '/';
  }

  window.__EVENTORE_BASE__ = siteBase();

  var stale = document.querySelector('base[data-eventore-base]');
  if (stale) stale.parentNode.removeChild(stale);
})();

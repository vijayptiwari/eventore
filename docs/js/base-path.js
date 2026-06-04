/**
 * Site root for GitHub Pages (/repo/) and local serve (/).
 * Assets and header/footer URLs always resolve from the docs root, not /guide/.
 */
(function () {
  var STREAM_SLUGS = [
    'kafka',
    'pulsar',
    'rabbitmq',
    'mqtt',
    'jms',
    'kinesis',
    'gcp-pubsub',
    'azure-service-bus',
  ];

  function docsRoot(path) {
    var guideRoot = path.match(/^(.*)\/guide(?:\/|$)/);
    if (guideRoot) {
      var prefix = guideRoot[1];
      return (prefix ? prefix : '') + '/';
    }
    if (/\.[a-z0-9]+$/i.test(path)) {
      return path.replace(/\/[^/]*$/, '/') || '/';
    }
    if (path.endsWith('/')) return path;
    var tail = path.match(/^(.*)\/([^/]+)$/);
    if (tail && STREAM_SLUGS.indexOf(tail[2].replace(/\.html$/i, '')) >= 0) {
      return (tail[1] ? tail[1] : '') + '/';
    }
    return path + '/';
  }

  function redirectIfNeeded(path) {
    var root = docsRoot(path);

    if (path === root + 'guide' || path === root.replace(/\/$/, '') + '/guide') {
      location.replace(root + 'guide/index.html' + location.search + location.hash);
      return true;
    }

    var guidePage = path.match(/^(.*\/guide\/[a-z0-9-]+)\/$/);
    if (guidePage) {
      location.replace(guidePage[1] + '.html' + location.hash);
      return true;
    }

    if (path === root + 'guide/developers' || path === root + 'guide/developers.html') {
      location.replace(root + 'about.html#developer' + location.hash);
      return true;
    }
    if (path === root + 'guide/contributing' || path === root + 'guide/contributing.html') {
      location.replace(root + 'about.html#contribute' + location.hash);
      return true;
    }

    for (var i = 0; i < STREAM_SLUGS.length; i++) {
      var slug = STREAM_SLUGS[i];
      if (path === root + slug || path === root + slug + '.html') {
        location.replace(root + 'guide/' + slug + '.html' + location.hash);
        return true;
      }
    }
    return false;
  }

  var path = location.pathname.replace(/\\/g, '/');
  if (redirectIfNeeded(path)) return;

  window.__EVENTORE_BASE__ = docsRoot(path);

  var stale = document.querySelector('base[data-eventore-base]');
  if (stale) stale.parentNode.removeChild(stale);
})();

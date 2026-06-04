(function () {
  var TAB_IDS = ['project', 'developer', 'contribute'];
  var HASH_MAP = {
    about: 'project',
    project: 'project',
    developer: 'developer',
    'about-developer': 'developer',
    contribute: 'contribute',
    contributing: 'contribute',
  };

  function panelId(tab) {
    return 'about-panel-' + tab;
  }

  function setTab(tab, replaceHash) {
    if (TAB_IDS.indexOf(tab) < 0) tab = 'project';
    var tabs = document.querySelectorAll('[data-about-tab]');
    var panels = document.querySelectorAll('[data-about-panel]');
    tabs.forEach(function (btn) {
      var on = btn.getAttribute('data-about-tab') === tab;
      btn.classList.toggle('active', on);
      btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    panels.forEach(function (panel) {
      var on = panel.getAttribute('data-about-panel') === tab;
      panel.classList.toggle('about-panel--hidden', !on);
      panel.hidden = !on;
    });
    if (replaceHash !== false) {
      var hash = tab === 'project' ? 'about' : tab === 'developer' ? 'developer' : 'contribute';
      if (location.hash.replace('#', '') !== hash) {
        history.replaceState(null, '', '#' + hash);
      }
    }
  }

  function tabFromHash() {
    var raw = (location.hash || '#about').replace(/^#/, '').toLowerCase();
    return HASH_MAP[raw] || 'project';
  }

  document.querySelectorAll('[data-about-tab]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      setTab(btn.getAttribute('data-about-tab'));
    });
  });

  window.addEventListener('hashchange', function () {
    setTab(tabFromHash(), false);
  });

  setTab(tabFromHash(), false);
})();

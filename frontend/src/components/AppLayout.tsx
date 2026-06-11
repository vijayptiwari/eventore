import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { isApiAuthError } from '../api/client';
import { useAppConfig } from '../hooks/useAppConfig';
import { logoLightSrc } from '../config/portalMeta';
import ApiTokenSettingsDialog from './ApiTokenSettingsDialog';
import PortalAboutDialog from './PortalAboutDialog';
import StreamsSidePanel from './StreamsSidePanel';

export default function AppLayout() {
  const queryClient = useQueryClient();
  const { data: config, error: configError } = useAppConfig();
  const [aboutOpen, setAboutOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const authFailed = isApiAuthError(configError);

  return (
    <div className="app-shell app-shell-wide">
      {config?.deploymentMode === 'READONLY' && (
        <div className="banner-readonly">
          Read-only deployment — publish and connection changes are disabled.
        </div>
      )}
      {authFailed && (
        <div className="banner-auth-error">
          API authentication failed (401) —{' '}
          <button type="button" className="banner-link-btn" onClick={() => setSettingsOpen(true)}>
            open Settings
          </button>{' '}
          and enter your API token.
        </div>
      )}
      <header className="app-topbar">
        <nav className="nav-inner" aria-label="Main">
          <button
            type="button"
            className="brand-logo-btn"
            onClick={() => setAboutOpen(true)}
            aria-haspopup="dialog"
            title="About Eventore"
          >
            <img
              className="brand-logo"
              src={logoLightSrc}
              alt="Eventore — open about & links"
              width={220}
              height={44}
              decoding="async"
            />
          </button>
          <NavLink to="/">Dashboard</NavLink>
          <NavLink to="/connections">Connections</NavLink>
          <NavLink to="/browse">Browse</NavLink>
          <NavLink to="/stream">Live Stream</NavLink>
          <button
            type="button"
            className="nav-settings-btn"
            onClick={() => setSettingsOpen(true)}
            title="API token settings"
          >
            Settings
          </button>
          {config && <span className="tag nav-mode-tag">{config.deploymentMode}</span>}
        </nav>
      </header>
      <div className="app-body">
        <StreamsSidePanel />
        <main className="app-main">
          <Outlet />
        </main>
      </div>
      <PortalAboutDialog open={aboutOpen} onClose={() => setAboutOpen(false)} />
      <ApiTokenSettingsDialog
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        onSaved={() => {
          void queryClient.invalidateQueries({ queryKey: ['config'] });
        }}
      />
    </div>
  );
}

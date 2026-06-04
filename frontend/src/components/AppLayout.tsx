import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAppConfig } from '../hooks/useAppConfig';
import { logoLightSrc } from '../config/portalMeta';
import PortalAboutDialog from './PortalAboutDialog';
import StreamsSidePanel from './StreamsSidePanel';

export default function AppLayout() {
  const { data: config } = useAppConfig();
  const [aboutOpen, setAboutOpen] = useState(false);

  return (
    <div className="app-shell app-shell-wide">
      {config?.deploymentMode === 'READONLY' && (
        <div className="banner-readonly">
          Read-only deployment — publish and connection changes are disabled.
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
    </div>
  );
}

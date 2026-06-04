import { NavLink, Outlet } from 'react-router-dom';
import { useAppConfig } from '../hooks/useAppConfig';
import StreamsSidePanel from './StreamsSidePanel';

export default function AppLayout() {
  const { data: config } = useAppConfig();

  return (
    <div className="app-shell">
      {config?.deploymentMode === 'READONLY' && (
        <div className="banner-readonly">
          Read-only deployment — publish and connection changes are disabled.
        </div>
      )}
      <nav>
        <span className="brand">Eventore</span>
        <NavLink to="/">Dashboard</NavLink>
        <NavLink to="/connections">Connections</NavLink>
        <NavLink to="/browse">Browse</NavLink>
        <NavLink to="/stream">Live Stream</NavLink>
        {config && (
          <span className="tag" style={{ marginLeft: 'auto' }}>
            {config.deploymentMode}
          </span>
        )}
      </nav>
      <div className="app-body">
        <StreamsSidePanel />
        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

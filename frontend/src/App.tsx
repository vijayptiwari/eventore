import { Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import { StreamWorkspaceProvider } from './stream/StreamWorkspaceContext';
import DashboardPage from './pages/DashboardPage';
import ConnectionsPage from './pages/ConnectionsPage';
import BrowsePage from './pages/BrowsePage';
import StreamPage from './pages/StreamPage';
import BridgesPage from './pages/BridgesPage';

export default function App() {
  return (
    <StreamWorkspaceProvider>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/connections" element={<ConnectionsPage />} />
          <Route path="/browse" element={<BrowsePage />} />
          <Route path="/stream" element={<StreamPage />} />
          <Route path="/bridges" element={<BridgesPage />} />
          <Route path="/replication" element={<BridgesPage />} />
        </Route>
      </Routes>
    </StreamWorkspaceProvider>
  );
}

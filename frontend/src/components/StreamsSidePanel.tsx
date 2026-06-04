import { Link, useNavigate } from 'react-router-dom';
import { useStreamWorkspace } from '../stream/StreamWorkspaceContext';

function statusClass(status: string): string {
  switch (status) {
    case 'active':
      return 'stream-status stream-status-active';
    case 'connecting':
      return 'stream-status stream-status-connecting';
    case 'error':
      return 'stream-status stream-status-error';
    default:
      return 'stream-status';
  }
}

export default function StreamsSidePanel() {
  const {
    wsConnected,
    sessions,
    activeSessionId,
    setActiveSessionId,
    removeStream,
    startStream,
    stopStream,
    restartAllStreams,
  } = useStreamWorkspace();
  const navigate = useNavigate();

  return (
    <aside className="streams-side-panel">
      <div className="streams-side-header">
        <h2>Live streams</h2>
        <span className={`tag ${wsConnected ? 'tag-ok' : 'tag-warn'}`}>
          {wsConnected ? 'WS connected' : 'WS reconnecting'}
        </span>
      </div>
      <p className="streams-side-hint">
        Sessions are saved in cookies — reopen this tab to restore your streams.
      </p>
      <div className="streams-side-actions">
        <button type="button" className="secondary" onClick={() => navigate('/stream')}>
          + Add stream
        </button>
        {sessions.length > 0 && (
          <button type="button" className="secondary" onClick={restartAllStreams}>
            Reconnect all
          </button>
        )}
      </div>
      <ul className="streams-list">
        {sessions.length === 0 && (
          <li className="streams-empty">
            No active streams.{' '}
            <Link to="/browse">Browse</Link> destinations or{' '}
            <Link to="/stream">add one</Link>.
          </li>
        )}
        {sessions.map((s) => (
          <li
            key={s.id}
            className={`streams-list-item ${s.id === activeSessionId ? 'streams-list-item-active' : ''}`}
          >
            <button
              type="button"
              className="streams-list-select"
              onClick={() => {
                setActiveSessionId(s.id);
                navigate('/stream');
              }}
            >
              <span className="streams-list-title">{s.connectionName}</span>
              <span className="streams-list-dest">{s.destination}</span>
              <span className="streams-list-meta">
                <span className={statusClass(s.status)}>{s.status}</span>
                <span className="tag">{s.protocol}</span>
                {s.messageCount > 0 && (
                  <span className="tag">{s.messageCount} msgs</span>
                )}
              </span>
              {s.lastError && <span className="streams-list-error">{s.lastError}</span>}
            </button>
            <div className="streams-list-controls">
              {s.status === 'stopped' || s.status === 'error' || s.status === 'idle' ? (
                <button type="button" className="secondary" onClick={() => startStream(s.id)}>
                  Start
                </button>
              ) : (
                <button type="button" className="secondary" onClick={() => stopStream(s.id)}>
                  Stop
                </button>
              )}
              <button type="button" className="secondary" onClick={() => removeStream(s.id)}>
                Remove
              </button>
            </div>
          </li>
        ))}
      </ul>
    </aside>
  );
}

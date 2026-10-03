import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { useStreamWorkspace } from '../stream/StreamWorkspaceContext';
import type { LiveStreamSession, LiveViewDurationMinutes, ReplayMode } from '../stream/types';
import ExportResultActions from './ExportResultActions';
import MessagePayloadViewer from './MessagePayloadViewer';

const DURATIONS: LiveViewDurationMinutes[] = [1, 2, 5, 10];
const MAX_REGEX_LENGTH = 512;

interface Props {
  session: LiveStreamSession;
}

export default function LiveViewPanel({ session }: Props) {
  const { wsConnected, liveViews, startLiveView, stopLiveView } = useStreamWorkspace();
  const liveView = liveViews[session.id];
  const [topicFilter, setTopicFilter] = useState('');
  const [selectedTopics, setSelectedTopics] = useState<Set<string>>(() => new Set([session.destination]));
  const [headerRegex, setHeaderRegex] = useState('');
  const [bodyRegex, setBodyRegex] = useState('');
  const [durationMinutes, setDurationMinutes] = useState<LiveViewDurationMinutes>(2);
  const [replayMode, setReplayMode] = useState<ReplayMode>('LATEST');
  const [replayTimestamp, setReplayTimestamp] = useState('');
  const [replayOffset, setReplayOffset] = useState('');
  const [remainingSec, setRemainingSec] = useState<number | null>(null);

  const { data: topics } = useQuery({
    // Distinct from StreamInspector's 'inspect-topics' key: this query has
    // different `enabled` rules and must not share cache entries with it.
    queryKey: ['live-view-topics', session.connectionId, topicFilter],
    queryFn: () => api.inspectTopics(session.connectionId, topicFilter || undefined),
    enabled:
      session.protocol === 'KAFKA' ||
      session.protocol === 'PULSAR' ||
      session.protocol === 'RABBITMQ' ||
      session.protocol === 'KINESIS' ||
      session.protocol === 'GCP_PUBSUB' ||
      session.protocol === 'AZURE_SERVICE_BUS',
  });

  const availableTopics = useMemo(() => {
    const names = new Set<string>([session.destination]);
    topics?.forEach((t) => names.add(t.name));
    return [...names].sort((a, b) => a.localeCompare(b));
  }, [topics, session.destination]);

  useEffect(() => {
    const expiresAt = liveView?.expiresAt;
    if (!expiresAt || liveView.status !== 'active') {
      setRemainingSec(null);
      return;
    }
    const tick = () => {
      const sec = Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
      setRemainingSec(sec);
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [liveView?.expiresAt, liveView?.status]);

  const toggleTopic = (name: string) => {
    setSelectedTopics((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  const isActive = liveView?.active && (liveView.status === 'connecting' || liveView.status === 'active');
  const regexTooLong =
    headerRegex.length > MAX_REGEX_LENGTH || bodyRegex.length > MAX_REGEX_LENGTH;

  return (
    <div className="live-view-panel">
      <p className="inspector-meta">
        Temporary live view across selected topics (all partitions). Only one live view per stream at a time.
        Max duration 10 minutes.
      </p>

      {!wsConnected && <p className="stream-error">WebSocket disconnected — reconnecting…</p>}

      <div className="card">
        <h3>Topic selection</h3>
        <div className="form-row">
          <label>Filter</label>
          <input value={topicFilter} onChange={(e) => setTopicFilter(e.target.value)} placeholder="name contains…" />
        </div>
        <div className="live-view-topics">
          {availableTopics.map((name) => (
            <label key={name} className="live-view-topic-chip">
              <input
                type="checkbox"
                checked={selectedTopics.has(name)}
                onChange={() => toggleTopic(name)}
                disabled={isActive}
              />
              {name}
            </label>
          ))}
        </div>
        <p className="inspector-meta">{selectedTopics.size} topic(s) selected</p>
      </div>

      <div className="card">
        <h3>Regex filters</h3>
        <div className="form-row">
          <label>Header regex (matches serialized key=value lines)</label>
          <input
            value={headerRegex}
            onChange={(e) => setHeaderRegex(e.target.value)}
            placeholder="e.g. correlationId=order-.*"
            maxLength={MAX_REGEX_LENGTH}
            disabled={isActive}
          />
        </div>
        <div className="form-row">
          <label>Body / payload regex</label>
          <input
            value={bodyRegex}
            onChange={(e) => setBodyRegex(e.target.value)}
            placeholder='e.g. "status":"OK"'
            maxLength={MAX_REGEX_LENGTH}
            disabled={isActive}
          />
        </div>
        {regexTooLong && (
          <p className="stream-error">Each regex filter must be at most {MAX_REGEX_LENGTH} characters.</p>
        )}
        <p className="inspector-meta">Leave blank to show all messages. Invalid regex is rejected by the server.</p>
      </div>

      <div className="card">
        <h3>Starting Point & Time-Travel Replay</h3>
        <p className="inspector-meta">
          Control where message consumption begins: real-time latest, beginning of topic, specific timestamp, or partition offset.
        </p>
        <div className="inspector-tabs" style={{ marginBottom: '0.75rem' }}>
          {(['LATEST', 'EARLIEST', 'TIMESTAMP', 'OFFSET'] as ReplayMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              className={replayMode === mode ? 'inspector-tab active' : 'inspector-tab'}
              disabled={isActive}
              onClick={() => setReplayMode(mode)}
            >
              {mode === 'LATEST' && 'Latest (Real-time)'}
              {mode === 'EARLIEST' && 'Earliest (Beginning)'}
              {mode === 'TIMESTAMP' && 'Timestamp (Time-Travel)'}
              {mode === 'OFFSET' && 'Specific Offset'}
            </button>
          ))}
        </div>

        {replayMode === 'TIMESTAMP' && (
          <div className="form-row" style={{ marginTop: '0.5rem' }}>
            <label>Replay From Timestamp (ISO-8601 or local datetime)</label>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <input
                type="text"
                value={replayTimestamp}
                onChange={(e) => setReplayTimestamp(e.target.value)}
                placeholder="e.g. 2026-10-03T18:00:00Z"
                disabled={isActive}
                style={{ flex: 1 }}
              />
              <button
                type="button"
                className="btn btn-secondary"
                disabled={isActive}
                onClick={() => setReplayTimestamp(new Date(Date.now() - 5 * 60_000).toISOString())}
              >
                5m ago
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={isActive}
                onClick={() => setReplayTimestamp(new Date(Date.now() - 60 * 60_000).toISOString())}
              >
                1h ago
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={isActive}
                onClick={() => setReplayTimestamp(new Date(Date.now() - 24 * 60 * 60_000).toISOString())}
              >
                1d ago
              </button>
            </div>
          </div>
        )}

        {replayMode === 'OFFSET' && (
          <div className="form-row" style={{ marginTop: '0.5rem' }}>
            <label>Starting Partition Offset</label>
            <input
              type="number"
              value={replayOffset}
              onChange={(e) => setReplayOffset(e.target.value)}
              placeholder="e.g. 1000"
              disabled={isActive}
            />
          </div>
        )}
      </div>

      <div className="card">
        <h3>Duration</h3>
        <div className="inspector-tabs">
          {DURATIONS.map((d) => (
            <button
              key={d}
              type="button"
              className={durationMinutes === d ? 'inspector-tab active' : 'inspector-tab'}
              disabled={isActive}
              onClick={() => setDurationMinutes(d)}
            >
              {d} min
            </button>
          ))}
        </div>
        {remainingSec != null && liveView?.status === 'active' && (
          <p className="inspector-meta">
            Time remaining: {Math.floor(remainingSec / 60)}:{String(remainingSec % 60).padStart(2, '0')}
          </p>
        )}
        <div className="inspector-actions">
          <button
            type="button"
            disabled={!wsConnected || isActive || selectedTopics.size === 0 || regexTooLong}
            onClick={() =>
              startLiveView(session.id, {
                topics: [...selectedTopics],
                headerRegex,
                bodyRegex,
                durationMinutes,
                replayMode,
                replayTimestamp: replayMode === 'TIMESTAMP' ? replayTimestamp : undefined,
                replayOffset: replayMode === 'OFFSET' && replayOffset ? parseInt(replayOffset, 10) : undefined,
              })
            }
          >
            Start live view
          </button>
          <button
            type="button"
            className="secondary"
            disabled={!isActive}
            onClick={() => stopLiveView(session.id)}
          >
            Stop
          </button>
        </div>
        {liveView?.lastError && <p className="stream-error">{liveView.lastError}</p>}
        {liveView?.status === 'expired' && (
          <p className="inspector-meta">Live view ended (time limit reached).</p>
        )}
      </div>

      <div className="card messages-panel stream-messages-panel">
        <h3>
          Live view messages ({liveView?.messages.length ?? 0})
          {liveView?.topics.length ? ` — ${liveView.topics.join(', ')}` : ''}
        </h3>
        <ExportResultActions
          filenameBase={`live-view_${session.connectionName}_${session.id.slice(0, 8)}`}
          messages={liveView?.messages}
          meta={{
            connectionId: session.connectionId,
            protocol: session.protocol,
            topics: liveView?.topics,
            headerRegex: liveView?.headerRegex,
            bodyRegex: liveView?.bodyRegex,
            durationMinutes: liveView?.durationMinutes,
            expiresAt: liveView?.expiresAt,
            status: liveView?.status,
          }}
        />
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Topic</th>
              <th>Partition</th>
              <th>Payload</th>
            </tr>
          </thead>
          <tbody>
            {(liveView?.messages ?? []).map((m) => (
              <tr key={m.id}>
                <td>{new Date(m.timestamp).toLocaleTimeString()}</td>
                <td>{m.destination}</td>
                <td>{m.headers?.partition ?? '—'}</td>
                <td className="message-payload">
                  <MessagePayloadViewer payload={m.payload} headers={m.headers} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!liveView?.messages.length && <p>{isActive ? 'Waiting for matching messages…' : 'Start a live view to stream messages.'}</p>}
      </div>
    </div>
  );
}

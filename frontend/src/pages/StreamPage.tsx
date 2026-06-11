import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import StreamInspector from '../components/StreamInspector';
import { api, canAction } from '../api/client';
import { useAppConfig } from '../hooks/useAppConfig';
import { useStreamWorkspace } from '../stream/StreamWorkspaceContext';

export default function StreamPage() {
  const [params] = useSearchParams();
  const { data: config } = useAppConfig();
  const { data: connections } = useQuery({
    queryKey: ['connections'],
    queryFn: api.listConnections,
  });
  const { wsConnected, activeSession, addStream } = useStreamWorkspace();

  const [connectionId, setConnectionId] = useState(params.get('connectionId') ?? '');
  const [destination, setDestination] = useState(params.get('destination') ?? '');
  const [publishBody, setPublishBody] = useState('{"hello":"eventore"}');
  const canPublish = canAction(config?.allowedActions, 'PUBLISH');

  const urlStreamHandled = useRef<string | null>(null);
  useEffect(() => {
    const cid = params.get('connectionId');
    const dest = params.get('destination');
    if (cid) setConnectionId(cid);
    if (dest) setDestination(dest);
    if (!cid || !dest || !connections?.length) return;
    const key = `${cid}:${dest}`;
    if (urlStreamHandled.current === key) return;
    const conn = connections.find((c) => c.id === cid);
    if (conn) {
      urlStreamHandled.current = key;
      addStream({
        connectionId: cid,
        connectionName: conn.name,
        protocol: conn.protocol,
        destination: dest,
        autoStart: true,
      });
    }
  }, [params, connections, addStream]);

  const publishMutation = useMutation({
    mutationFn: () => {
      if (!activeSession) {
        throw new Error('Select an active stream before publishing');
      }
      return api.publish(activeSession.connectionId, {
        destination: activeSession.destination,
        payload: publishBody,
        contentType: 'application/json',
      });
    },
  });

  const handleAddStream = () => {
    const conn = connections?.find((c) => c.id === connectionId);
    if (!conn?.id || !destination) return;
    addStream({
      connectionId: conn.id,
      connectionName: conn.name,
      protocol: conn.protocol,
      destination,
      autoStart: true,
    });
  };

  const display = activeSession;

  return (
    <div className="stream-page">
      <h1>Live stream & inspector</h1>
      <p className="stream-page-meta">
        WebSocket:{' '}
        <span className={`tag ${wsConnected ? 'tag-ok' : 'tag-warn'}`}>
          {wsConnected ? 'connected' : 'reconnecting'}
        </span>
        {display && (
          <>
            {' '}
            — <strong>{display.connectionName}</strong> / {display.protocol} / {display.destination}
          </>
        )}
      </p>

      <div className="card">
        <h2>Add stream</h2>
        <div className="form-grid">
          <div className="form-row">
            <label>Connection</label>
            <select value={connectionId} onChange={(e) => setConnectionId(e.target.value)}>
              <option value="">Select...</option>
              {connections?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.protocol})
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>Destination</label>
            <input value={destination} onChange={(e) => setDestination(e.target.value)} />
          </div>
        </div>
        <button type="button" disabled={!connectionId || !destination} onClick={handleAddStream}>
          Add to side panel
        </button>
      </div>

      {!display && (
        <div className="card">
          <p>Select a stream from the side panel to inspect brokers, topics, consumer groups, lag, and messages.</p>
        </div>
      )}

      {display && (
        <>
          {display.lastError && <p className="stream-error">{display.lastError}</p>}
          {canPublish && (
            <div className="card">
              <h2>Publish to {display.destination}</h2>
              <textarea rows={3} value={publishBody} onChange={(e) => setPublishBody(e.target.value)} />
              <button
                type="button"
                style={{ marginTop: '0.5rem' }}
                disabled={publishMutation.isPending}
                onClick={() => publishMutation.mutate()}
              >
                Publish
              </button>
              {publishMutation.isError && (
                <p className="stream-error">{String(publishMutation.error)}</p>
              )}
              {publishMutation.isSuccess && <p className="tag tag-ok">Published</p>}
            </div>
          )}
          <StreamInspector session={display} />
        </>
      )}
    </div>
  );
}

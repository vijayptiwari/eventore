import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useStreamWorkspace } from '../stream/StreamWorkspaceContext';

export default function BrowsePage() {
  const navigate = useNavigate();
  const { addStream } = useStreamWorkspace();
  const { data: connections } = useQuery({
    queryKey: ['connections'],
    queryFn: api.listConnections,
  });
  const [selectedId, setSelectedId] = useState<string>('');

  const connectionId = selectedId || connections?.[0]?.id || '';

  const { data: destinations, isLoading, error } = useQuery({
    queryKey: ['destinations', connectionId],
    queryFn: () => api.listDestinations(connectionId),
    enabled: !!connectionId,
  });

  const addToStreams = (destination: string) => {
    const conn = connections?.find((c) => c.id === connectionId);
    if (!conn?.id) return;
    addStream({
      connectionId: conn.id,
      connectionName: conn.name,
      protocol: conn.protocol,
      destination,
      autoStart: true,
    });
    navigate('/stream');
  };

  if (connections && connections.length === 0) {
    return (
      <div>
        <h1>Browse destinations</h1>
        <div className="card">
          <p>
            No connections yet. <Link to="/connections">Create a connection</Link> to browse its
            topics and queues.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1>Browse destinations</h1>
      <div className="card form-row" style={{ maxWidth: 400 }}>
        <label>Connection</label>
        <select value={connectionId} onChange={(e) => setSelectedId(e.target.value)}>
          {connections?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.protocol})
            </option>
          ))}
        </select>
      </div>
      <div className="card">
        {isLoading && <p>Loading destinations...</p>}
        {error && <p style={{ color: '#f87171' }}>{String(error)}</p>}
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {destinations?.map((d) => (
              <tr key={`${d.name}:${d.type}`}>
                <td>{d.name}</td>
                <td>{d.type}</td>
                <td>
                  <button type="button" onClick={() => addToStreams(d.name)}>
                    Add to streams
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!destinations?.length && !isLoading && <p>No destinations found.</p>}
      </div>
    </div>
  );
}

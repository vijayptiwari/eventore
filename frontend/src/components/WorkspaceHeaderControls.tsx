import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthAndWorkspaces } from '../hooks/useAuthAndWorkspaces';
import { api } from '../api/client';

export default function WorkspaceHeaderControls() {
  const queryClient = useQueryClient();
  const {
    principal,
    workspaces,
    activeWorkspaceId,
    setActiveWorkspaceId,
    clusterStatus,
  } = useAuthAndWorkspaces();

  const [createOpen, setCreateOpen] = useState(false);
  const [newId, setNewId] = useState('');
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const canCreateWorkspace = principal?.isPlatformAdmin || principal?.roles?.includes('WORKSPACE_ADMIN');

  const handleCreateWorkspace = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newId.trim() || !newName.trim()) return;
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await api.createWorkspace({
        id: newId.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '-'),
        name: newName.trim(),
        description: newDesc.trim(),
        tags: ['custom'],
      });
      await queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      setActiveWorkspaceId(newId.trim().toLowerCase());
      setCreateOpen(false);
      setNewId('');
      setNewName('');
      setNewDesc('');
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : String(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const primaryRole = principal?.roles?.[0] || 'VIEWER';
  const roleDisplay = primaryRole.replace(/_/g, ' ');

  return (
    <div className="workspace-header-controls" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
      {/* Workspace Selector */}
      <div className="workspace-selector-container" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
        <label htmlFor="workspace-select" style={{ fontSize: '0.8rem', color: '#9aa5b1', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
          <span>📁</span>
          <span>Workspace:</span>
        </label>
        <select
          id="workspace-select"
          className="workspace-select"
          value={activeWorkspaceId}
          onChange={(e) => setActiveWorkspaceId(e.target.value)}
          style={{
            background: '#161d27',
            color: '#e8eaed',
            border: '1px solid #2b394a',
            borderRadius: '4px',
            padding: '0.2rem 0.5rem',
            fontSize: '0.85rem',
            cursor: 'pointer',
          }}
        >
          {workspaces.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
          {workspaces.every((w) => w.id !== activeWorkspaceId) && (
            <option value={activeWorkspaceId}>{activeWorkspaceId}</option>
          )}
        </select>
        {canCreateWorkspace && (
          <button
            type="button"
            className="btn-create-workspace"
            onClick={() => setCreateOpen(true)}
            title="Create new workspace"
            style={{
              background: 'transparent',
              border: '1px dashed #3f526b',
              color: '#7cb8ff',
              borderRadius: '4px',
              padding: '0.15rem 0.4rem',
              fontSize: '0.8rem',
              cursor: 'pointer',
            }}
          >
            + New
          </button>
        )}
      </div>

      {/* Cluster Bus Status Badge */}
      {clusterStatus && (
        <span
          className="cluster-status-badge"
          title={`Node: ${clusterStatus.nodeId} | Broadcasts: ${clusterStatus.totalBroadcasts} | Subscribers: ${clusterStatus.activeSubscribers}`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.3rem',
            fontSize: '0.75rem',
            padding: '0.15rem 0.5rem',
            borderRadius: '4px',
            background: clusterStatus.mode === 'REDIS' ? '#1a3322' : '#192636',
            color: clusterStatus.mode === 'REDIS' ? '#7ee787' : '#7cb8ff',
            border: `1px solid ${clusterStatus.mode === 'REDIS' ? '#2ea043' : '#388bfd44'}`,
          }}
        >
          <span style={{ fontSize: '0.7rem' }}>⚡</span>
          <span>{clusterStatus.mode}</span>
          {clusterStatus.connectedPeers > 1 && (
            <span style={{ opacity: 0.8 }}>({clusterStatus.connectedPeers} pods)</span>
          )}
        </span>
      )}

      {/* Authenticated User Principal Badge */}
      {principal && (
        <div
          className="user-principal-badge"
          title={`${principal.email} (${principal.userId}) — Assigned: ${principal.assignedWorkspaces.join(', ')}`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.35rem',
            fontSize: '0.8rem',
            background: '#161f2e',
            border: '1px solid #28374d',
            borderRadius: '16px',
            padding: '0.2rem 0.6rem',
            color: '#c9d1d9',
          }}
        >
          <span style={{ fontSize: '0.85rem' }}>👤</span>
          <span style={{ fontWeight: 500 }}>{principal.displayName || principal.email}</span>
          <span
            style={{
              fontSize: '0.68rem',
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
              background: '#21262d',
              color: '#8b949e',
              padding: '0.05rem 0.35rem',
              borderRadius: '8px',
            }}
          >
            {roleDisplay}
          </span>
        </div>
      )}

      {/* Create Workspace Modal */}
      {createOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Create Workspace"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: '#131922',
              border: '1px solid #2b394a',
              borderRadius: '8px',
              padding: '1.5rem',
              width: '90%',
              maxWidth: '28rem',
              boxShadow: '0 8px 32px rgba(0, 0, 0, 0.5)',
            }}
          >
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.1rem', color: '#e8eaed' }}>Create Multi-Tenant Workspace</h3>
            {errorMsg && (
              <div style={{ background: '#4a1515', color: '#ffb4b4', padding: '0.5rem', borderRadius: '4px', fontSize: '0.85rem', marginBottom: '1rem' }}>
                {errorMsg}
              </div>
            )}
            <form onSubmit={handleCreateWorkspace}>
              <div style={{ marginBottom: '0.85rem' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#9aa5b1', marginBottom: '0.25rem' }}>
                  Workspace Slug / ID (e.g. payments, analytics):
                </label>
                <input
                  type="text"
                  required
                  value={newId}
                  onChange={(e) => setNewId(e.target.value)}
                  placeholder="e.g. payments-prod"
                  style={{ width: '100%', padding: '0.5rem', borderRadius: '4px', border: '1px solid #2b394a', background: '#0c1117', color: '#e8eaed' }}
                />
              </div>
              <div style={{ marginBottom: '0.85rem' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#9aa5b1', marginBottom: '0.25rem' }}>
                  Display Name:
                </label>
                <input
                  type="text"
                  required
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. Payments & Settlement"
                  style={{ width: '100%', padding: '0.5rem', borderRadius: '4px', border: '1px solid #2b394a', background: '#0c1117', color: '#e8eaed' }}
                />
              </div>
              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#9aa5b1', marginBottom: '0.25rem' }}>
                  Description:
                </label>
                <textarea
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="Audited workspace for transactions and ledger event streams"
                  rows={2}
                  style={{ width: '100%', padding: '0.5rem', borderRadius: '4px', border: '1px solid #2b394a', background: '#0c1117', color: '#e8eaed' }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setCreateOpen(false)}
                  disabled={isSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Creating...' : 'Create Workspace'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

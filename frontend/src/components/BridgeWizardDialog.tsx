import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { ConnectionProfile, ReplicationBridge, ReplicationBridgeRequest } from '../api/types';

interface Props {
  open: boolean;
  onClose: () => void;
  initialBridge?: ReplicationBridge | null;
  onSaved: () => void;
}

interface RuleRow {
  id: string;
  type: 'SET' | 'RENAME' | 'REMOVE';
  key: string;
  value: string;
}

export default function BridgeWizardDialog({ open, onClose, initialBridge, onSaved }: Props) {
  const queryClient = useQueryClient();
  const { data: connections = [] } = useQuery<ConnectionProfile[]>({
    queryKey: ['connections'],
    queryFn: api.listConnections,
    enabled: open,
  });

  const [name, setName] = useState('');
  const [sourceConnectionId, setSourceConnectionId] = useState('');
  const [sourceDestination, setSourceDestination] = useState('');
  const [targetConnectionId, setTargetConnectionId] = useState('');
  const [targetDestination, setTargetDestination] = useState('');
  const [payloadFilter, setPayloadFilter] = useState('');
  const [loopPrevention, setLoopPrevention] = useState(true);
  const [autoStart, setAutoStart] = useState(true);
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (initialBridge) {
      setName(initialBridge.name);
      setSourceConnectionId(initialBridge.sourceConnectionId);
      setSourceDestination(initialBridge.sourceDestination);
      setTargetConnectionId(initialBridge.targetConnectionId);
      setTargetDestination(initialBridge.targetDestination);
      setPayloadFilter(initialBridge.payloadFilter || '');
      setLoopPrevention(initialBridge.loopPrevention);
      setAutoStart(initialBridge.enabled);

      // parse header transforms into rows
      const parsedRules: RuleRow[] = [];
      if (initialBridge.headerTransform) {
        Object.entries(initialBridge.headerTransform).forEach(([k, v], idx) => {
          if (k.startsWith('rename:')) {
            parsedRules.push({
              id: `rule-${idx}`,
              type: 'RENAME',
              key: k.substring('rename:'.length),
              value: v,
            });
          } else if (k.startsWith('remove:')) {
            parsedRules.push({
              id: `rule-${idx}`,
              type: 'REMOVE',
              key: k.substring('remove:'.length),
              value: '',
            });
          } else {
            parsedRules.push({
              id: `rule-${idx}`,
              type: 'SET',
              key: k,
              value: v,
            });
          }
        });
      }
      setRules(parsedRules);
    } else {
      setName('');
      setSourceConnectionId(connections[0]?.id || '');
      setSourceDestination('');
      setTargetConnectionId(connections[1]?.id || connections[0]?.id || '');
      setTargetDestination('');
      setPayloadFilter('');
      setLoopPrevention(true);
      setAutoStart(true);
      setRules([
        { id: 'default-1', type: 'SET', key: 'x-eventore-replicated-by', value: 'eventore-bridge' },
      ]);
    }
    setFormError(null);
  }, [open, initialBridge, connections]);

  const addRule = () => {
    setRules((prev) => [
      ...prev,
      { id: `rule-${Date.now()}-${Math.random()}`, type: 'SET', key: '', value: '' },
    ]);
  };

  const removeRule = (id: string) => {
    setRules((prev) => prev.filter((r) => r.id !== id));
  };

  const updateRule = (id: string, updates: Partial<RuleRow>) => {
    setRules((prev) =>
      prev.map((r) => (r.id === id ? { ...r, ...updates } : r)),
    );
  };

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!name.trim()) throw new Error('Pipeline name is required');
      if (!sourceConnectionId) throw new Error('Source connection is required');
      if (!sourceDestination.trim()) throw new Error('Source destination is required');
      if (!targetConnectionId) throw new Error('Target connection is required');
      if (!targetDestination.trim()) throw new Error('Target destination is required');

      // convert rule rows to headerTransform map
      const headerTransform: Record<string, string> = {};
      for (const r of rules) {
        const k = r.key.trim();
        if (!k) continue;
        if (r.type === 'RENAME') {
          headerTransform[`rename:${k}`] = r.value.trim();
        } else if (r.type === 'REMOVE') {
          headerTransform[`remove:${k}`] = '';
        } else {
          headerTransform[k] = r.value;
        }
      }

      const payload: ReplicationBridgeRequest = {
        name: name.trim(),
        sourceConnectionId,
        sourceDestination: sourceDestination.trim(),
        targetConnectionId,
        targetDestination: targetDestination.trim(),
        headerTransform: Object.keys(headerTransform).length > 0 ? headerTransform : undefined,
        payloadFilter: payloadFilter.trim() || undefined,
        loopPrevention,
        autoStart,
      };

      if (initialBridge?.id) {
        return api.updateBridge(initialBridge.id, payload);
      } else {
        return api.createBridge(payload);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['bridges'] });
      onSaved();
      onClose();
    },
    onError: (err: unknown) => {
      setFormError(err instanceof Error ? err.message : String(err));
    },
  });

  if (!open) return null;

  return (
    <div
      className="modal-backdrop"
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: '1rem',
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="card"
        style={{
          maxWidth: '750px',
          width: '100%',
          maxHeight: '90vh',
          overflowY: 'auto',
          background: '#0f172a',
          border: '1px solid #334155',
          borderRadius: '12px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)',
          padding: '1.5rem',
        }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="bridge-wizard-title"
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h2 id="bridge-wizard-title" style={{ margin: 0, fontSize: '1.25rem', color: '#f8fafc' }}>
            {initialBridge ? 'Edit Replication Pipeline' : 'Create Replication Bridge'}
          </h2>
          <button
            type="button"
            className="btn-secondary"
            onClick={onClose}
            style={{ padding: '0.25rem 0.6rem', fontSize: '1rem', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>

        {formError && (
          <div className="stream-error" style={{ marginBottom: '1rem', padding: '0.75rem', borderRadius: '6px' }}>
            {formError}
          </div>
        )}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate();
          }}
          style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}
        >
          {/* Pipeline Name */}
          <div>
            <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: '0.35rem', fontWeight: 600 }}>
              Pipeline Name *
            </label>
            <input
              type="text"
              className="form-input"
              style={{ width: '100%', padding: '0.5rem 0.75rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc' }}
              placeholder="e.g. Kafka Orders to RabbitMQ Ingestion"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>

          {/* Broker Route: Source & Target */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            {/* Source */}
            <div style={{ background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem' }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#38bdf8', fontWeight: 700, marginBottom: '0.75rem' }}>
                Source Broker (Ingress)
              </div>
              <div style={{ marginBottom: '0.75rem' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                  Connection *
                </label>
                <select
                  style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc' }}
                  value={sourceConnectionId}
                  onChange={(e) => setSourceConnectionId(e.target.value)}
                  required
                >
                  <option value="" disabled>Select Source Connection</option>
                  {connections.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.protocol})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                  Source Destination (Topic / Queue) *
                </label>
                <input
                  type="text"
                  style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace' }}
                  placeholder="e.g. orders.raw or orders-queue"
                  value={sourceDestination}
                  onChange={(e) => setSourceDestination(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Target */}
            <div style={{ background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem' }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#4ade80', fontWeight: 700, marginBottom: '0.75rem' }}>
                Target Broker (Egress)
              </div>
              <div style={{ marginBottom: '0.75rem' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                  Connection *
                </label>
                <select
                  style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc' }}
                  value={targetConnectionId}
                  onChange={(e) => setTargetConnectionId(e.target.value)}
                  required
                >
                  <option value="" disabled>Select Target Connection</option>
                  {connections.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.protocol})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                  Target Destination (Topic / Queue) *
                </label>
                <input
                  type="text"
                  style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace' }}
                  placeholder="e.g. orders.replicated or orders-dlq"
                  value={targetDestination}
                  onChange={(e) => setTargetDestination(e.target.value)}
                  required
                />
              </div>
            </div>
          </div>

          {/* Filtering & Loop Prevention */}
          <div style={{ background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem' }}>
            <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#e2e8f0', marginBottom: '0.75rem' }}>
              Filtering & Reliability Controls
            </div>

            <div style={{ marginBottom: '0.85rem' }}>
              <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem' }}>
                Payload Regex Filter (Optional)
              </label>
              <input
                type="text"
                style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace' }}
                placeholder='e.g. .*"region":\s*"US".* or .*"eventType":\s*"ORDER_COMPLETED".*'
                value={payloadFilter}
                onChange={(e) => setPayloadFilter(e.target.value)}
              />
              <span style={{ fontSize: '0.72rem', color: '#64748b' }}>
                If set, only messages matching this regex pattern will be replicated to the target.
              </span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.85rem', color: '#e2e8f0' }}>
                <input
                  type="checkbox"
                  checked={loopPrevention}
                  onChange={(e) => setLoopPrevention(e.target.checked)}
                />
                <span>
                  <strong>Automated Loop Prevention:</strong> Detect and drop echo loops using <code>x-eventore-bridge-id</code>
                </span>
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.85rem', color: '#e2e8f0' }}>
                <input
                  type="checkbox"
                  checked={autoStart}
                  onChange={(e) => setAutoStart(e.target.checked)}
                />
                <span>
                  <strong>Auto-Start Pipeline:</strong> Begin actively replicating messages immediately upon saving
                </span>
              </label>
            </div>
          </div>

          {/* Header Transformation Rules */}
          <div style={{ background: '#0b1120', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#e2e8f0' }}>
                Header Transformations & Metadata Injection
              </div>
              <button
                type="button"
                className="btn-secondary"
                onClick={addRule}
                style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem' }}
              >
                + Add Rule
              </button>
            </div>

            {rules.length === 0 ? (
              <p style={{ margin: 0, fontSize: '0.8rem', color: '#64748b', fontStyle: 'italic' }}>
                No custom header rules defined. Default provenance headers (<code>x-eventore-replicated</code>, <code>x-eventore-source-*</code>) are automatically attached.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {rules.map((r) => (
                  <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <select
                      style={{ padding: '0.4rem', background: '#020617', border: '1px solid #334155', borderRadius: '4px', color: '#f8fafc', fontSize: '0.75rem' }}
                      value={r.type}
                      onChange={(e) => updateRule(r.id, { type: e.target.value as RuleRow['type'] })}
                    >
                      <option value="SET">Set / Add Header</option>
                      <option value="RENAME">Rename Header</option>
                      <option value="REMOVE">Remove Header</option>
                    </select>

                    <input
                      type="text"
                      placeholder={r.type === 'RENAME' ? 'Old Header Key' : r.type === 'REMOVE' ? 'Header to Remove' : 'Header Key'}
                      style={{ flex: 1, padding: '0.4rem', background: '#020617', border: '1px solid #334155', borderRadius: '4px', color: '#f8fafc', fontSize: '0.75rem', fontFamily: 'monospace' }}
                      value={r.key}
                      onChange={(e) => updateRule(r.id, { key: e.target.value })}
                    />

                    {r.type !== 'REMOVE' && (
                      <input
                        type="text"
                        placeholder={r.type === 'RENAME' ? 'New Header Key' : 'Header Value'}
                        style={{ flex: 1, padding: '0.4rem', background: '#020617', border: '1px solid #334155', borderRadius: '4px', color: '#f8fafc', fontSize: '0.75rem' }}
                        value={r.value}
                        onChange={(e) => updateRule(r.id, { value: e.target.value })}
                      />
                    )}

                    <button
                      type="button"
                      className="btn-danger"
                      onClick={() => removeRule(r.id)}
                      style={{ padding: '0.3rem 0.5rem', fontSize: '0.75rem' }}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Form Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button type="button" className="btn-secondary" onClick={onClose} disabled={saveMutation.isPending}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Saving Pipeline...' : initialBridge ? 'Update Pipeline' : 'Create & Launch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

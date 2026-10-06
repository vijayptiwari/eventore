import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type DlqMessageInfo, type DlqRedriveRequest } from '../api/client';
import MessagePayloadViewer from './MessagePayloadViewer';

interface Props {
  connectionId: string;
  defaultTopic?: string;
  canPublish: boolean;
}

export default function StreamInspectorDlqTab({ connectionId, defaultTopic, canPublish }: Props) {
  const [selectedTopic, setSelectedTopic] = useState(defaultTopic || '');
  const [targetTopic, setTargetTopic] = useState('');
  const [activeMessage, setActiveMessage] = useState<DlqMessageInfo | null>(null);
  const [editPayload, setEditPayload] = useState('');
  const [redriveStatus, setRedriveStatus] = useState<string | null>(null);

  // 1. Fetch detected DLQ topics
  const {
    data: dlqTopics,
    isLoading: loadingTopics,
    refetch: refetchTopics,
  } = useQuery({
    queryKey: ['dlq-topics', connectionId],
    queryFn: () => api.listDlqTopics(connectionId),
  });

  // 2. Fetch dead-letter messages for selected topic
  const {
    data: dlqMessages,
    isLoading: loadingMessages,
    refetch: refetchMessages,
    error: messagesError,
  } = useQuery({
    queryKey: ['dlq-messages', connectionId, selectedTopic],
    queryFn: () => api.inspectDlqMessages(connectionId, selectedTopic, 50),
    enabled: !!selectedTopic,
  });

  // 3. Redrive mutation
  const redriveMutation = useMutation({
    mutationFn: (req: DlqRedriveRequest) => api.redriveDlq(connectionId, req),
    onSuccess: (data) => {
      setRedriveStatus(
        data.failedCount > 0
          ? `✗ Redriven ${data.redrivenCount}; ${data.failedCount} failed: ${data.errors.join('; ')}`
          : `✓ Redriven ${data.redrivenCount} message(s) to '${data.targetTopic}' successfully!`
      );
      if (data.failedCount === 0) setActiveMessage(null);
      refetchMessages();
    },
    onError: (err: unknown) => {
      setRedriveStatus(`✗ Redrive failed: ${err instanceof Error ? err.message : String(err)}`);
    },
  });

  const handleSelectDlq = (topicName: string, inferredTarget: string) => {
    setSelectedTopic(topicName);
    setTargetTopic(inferredTarget);
    setRedriveStatus(null);
  };

  const handleOpenRedriveModal = (item: DlqMessageInfo) => {
    setActiveMessage(item);
    setEditPayload(item.message.payload || '');
    if (!targetTopic) {
      setTargetTopic(item.originalTopic || selectedTopic.replace(/[-._](dlq|dlt|deadletter)$/i, ''));
    }
  };

  const handleExecuteSingleRedrive = () => {
    if (!activeMessage || !selectedTopic) return;
    redriveMutation.mutate({
      sourceTopic: selectedTopic,
      targetTopic: targetTopic || undefined,
      messageIds: [activeMessage.message.id],
      editedPayload: editPayload,
    });
  };

  const handleBatchRedrive = () => {
    if (!selectedTopic) return;
    const dest = targetTopic || selectedTopic.replace(/[-._](dlq|dlt|deadletter)$/i, '');
    if (!window.confirm(`Redrive all displayed dead-letter messages to '${dest}'?`)) {
      return;
    }
    redriveMutation.mutate({
      sourceTopic: selectedTopic,
      targetTopic: dest,
      maxMessages: 100,
    });
  };

  return (
    <div className="inspector-panel dlq-inspector">
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Dead Letter Queue (DLQ) & Redrive</h3>
          <button type="button" className="btn btn-secondary" onClick={() => refetchTopics()}>
            Scan DLQ Topics
          </button>
        </div>
        <p style={{ margin: '0.25rem 0 0.75rem', color: 'var(--text-muted, #888)' }}>
          Detects poison-pill topics, inspects error causes, and redrives messages back to primary queues.
        </p>

        {loadingTopics && <p>Scanning for DLQ topics…</p>}

        {dlqTopics && dlqTopics.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.5rem' }}>
            {dlqTopics.map((dlq) => (
              <button
                key={dlq.dlqTopic}
                type="button"
                className={`btn ${selectedTopic === dlq.dlqTopic ? 'btn-primary' : 'btn-secondary'}`}
                style={{ fontSize: '0.85rem', padding: '4px 10px' }}
                onClick={() => handleSelectDlq(dlq.dlqTopic, dlq.inferredTargetTopic)}
              >
                <strong>{dlq.dlqTopic}</strong>
                {dlq.inferredTargetTopic && (
                  <span style={{ opacity: 0.8, marginLeft: 6 }}>→ {dlq.inferredTargetTopic}</span>
                )}
              </button>
            ))}
          </div>
        ) : (
          !loadingTopics && <p style={{ fontStyle: 'italic', fontSize: '0.9rem' }}>No standard DLQ patterns auto-detected.</p>
        )}

        <div className="form-grid" style={{ marginTop: '0.75rem' }}>
          <div className="form-row">
            <label>DLQ Source Topic</label>
            <input
              value={selectedTopic}
              onChange={(e) => setSelectedTopic(e.target.value)}
              placeholder="e.g. orders.DLQ or payments-dlt"
            />
          </div>
          <div className="form-row">
            <label>Target Primary Topic</label>
            <input
              value={targetTopic}
              onChange={(e) => setTargetTopic(e.target.value)}
              placeholder="Inferred target or custom destination"
            />
          </div>
        </div>
      </div>

      {redriveStatus && (
        <div className={redriveStatus.startsWith('✓') ? 'tag tag-ok' : 'stream-error'} style={{ marginBottom: '1rem', padding: '8px 12px' }}>
          {redriveStatus}
        </div>
      )}

      {selectedTopic && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <h4 style={{ margin: 0 }}>
              Dead-letter messages on <code>{selectedTopic}</code>
              {dlqMessages && ` (${dlqMessages.length})`}
            </h4>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button type="button" className="btn btn-secondary" onClick={() => refetchMessages()}>
                Refresh
              </button>
              {canPublish && dlqMessages && dlqMessages.length > 0 && (
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={redriveMutation.isPending}
                  onClick={handleBatchRedrive}
                >
                  {redriveMutation.isPending ? 'Redriving…' : `Batch Redrive All (${dlqMessages.length})`}
                </button>
              )}
            </div>
          </div>

          {loadingMessages && <p>Reading dead-letter messages…</p>}
          {messagesError && <p className="stream-error">{String(messagesError)}</p>}

          {dlqMessages && dlqMessages.length === 0 && (
            <p style={{ fontStyle: 'italic' }}>Queue is clean! No dead-letter messages found on {selectedTopic}.</p>
          )}

          {dlqMessages && dlqMessages.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="table" style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border-color, #444)' }}>
                    <th style={{ padding: '8px' }}>Timestamp</th>
                    <th style={{ padding: '8px' }}>Original Target</th>
                    <th style={{ padding: '8px' }}>Failure Reason</th>
                    <th style={{ padding: '8px' }}>Payload Preview</th>
                    <th style={{ padding: '8px' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {dlqMessages.map((item) => (
                    <tr key={item.message.id} style={{ borderBottom: '1px solid var(--border-color, #333)' }}>
                      <td style={{ padding: '8px', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                        {item.message.timestamp ? new Date(item.message.timestamp).toLocaleTimeString() : '—'}
                      </td>
                      <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>
                        <code>{item.originalTopic || targetTopic || '—'}</code>
                      </td>
                      <td style={{ padding: '8px', maxWidth: '300px' }}>
                        {item.failureReason ? (
                          <span className="tag" style={{ background: '#7f1d1d', color: '#fca5a5' }} title={item.stackTraceSnippet}>
                            {item.failureReason.length > 60
                              ? item.failureReason.substring(0, 60) + '…'
                              : item.failureReason}
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted, #777)', fontStyle: 'italic' }}>Unknown reason</span>
                        )}
                      </td>
                      <td style={{ padding: '8px', maxWidth: '280px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <code>{item.message.payload ? item.message.payload.substring(0, 80) : ''}</code>
                      </td>
                      <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>
                        {canPublish ? (
                          <button
                            type="button"
                            className="btn btn-secondary"
                            style={{ padding: '3px 8px', fontSize: '0.85rem' }}
                            onClick={() => handleOpenRedriveModal(item)}
                          >
                            Inspect & Redrive
                          </button>
                        ) : (
                          <span style={{ opacity: 0.6, fontSize: '0.85rem' }}>Read-only</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Single Message Redrive / Edit Modal */}
      {activeMessage && (
        <div className="modal-backdrop" style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000
        }}>
          <div className="card modal-content" style={{ maxWidth: '650px', width: '90%', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ margin: 0 }}>Redrive Dead-Letter Message</h3>
              <button type="button" className="btn btn-secondary" onClick={() => setActiveMessage(null)}>✕</button>
            </div>

            {activeMessage.failureReason && (
              <div className="stream-error" style={{ marginBottom: '1rem' }}>
                <strong>Reason:</strong> {activeMessage.failureReason}
                {activeMessage.exceptionClass && <div><small>{activeMessage.exceptionClass}</small></div>}
              </div>
            )}

            <div className="form-row" style={{ marginBottom: '0.75rem' }}>
              <label>Target Destination Queue / Topic</label>
              <input
                value={targetTopic}
                onChange={(e) => setTargetTopic(e.target.value)}
                placeholder="e.g. orders"
              />
            </div>

            <div className="form-row" style={{ marginBottom: '0.75rem' }}>
              <label>Payload (editable before redrive)</label>
              <textarea
                rows={6}
                value={editPayload}
                onChange={(e) => setEditPayload(e.target.value)}
              />
            </div>

            <div style={{ marginBottom: '1rem' }}>
              <label style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 600 }}>Message Payload Viewer</label>
              <MessagePayloadViewer
                payload={activeMessage.message.payload || ''}
                headers={activeMessage.message.headers}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button type="button" className="btn btn-secondary" onClick={() => setActiveMessage(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={redriveMutation.isPending || !targetTopic}
                onClick={handleExecuteSingleRedrive}
              >
                {redriveMutation.isPending ? 'Redriving…' : `Redrive Message to '${targetTopic}'`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

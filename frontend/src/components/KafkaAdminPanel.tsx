import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, canAction } from '../api/client';
import type { KafkaAclEntry } from '../api/kafkaAdminTypes';
import { useAppConfig } from '../hooks/useAppConfig';

interface Props {
  connectionId: string;
  defaultTopic: string;
}

function parseHeaderLines(text: string): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || !trimmed.includes('=')) continue;
    const idx = trimmed.indexOf('=');
    headers[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
  }
  return headers;
}

const emptyAcl = (): KafkaAclEntry => ({
  resourceType: 'TOPIC',
  resourceName: '',
  patternType: 'LITERAL',
  principal: 'User:',
  host: '*',
  operation: 'READ',
  permissionType: 'ALLOW',
});

export default function KafkaAdminPanel({ connectionId, defaultTopic }: Props) {
  const { data: config } = useAppConfig();
  const canPublish = canAction(config?.allowedActions, 'PUBLISH');
  const canAdmin = canAction(config?.allowedActions, 'ADMIN_BROKER_OPS');
  const qc = useQueryClient();

  const [topic, setTopic] = useState(defaultTopic);
  const [publishKey, setPublishKey] = useState('');
  const [publishPartition, setPublishPartition] = useState('');
  const [publishPayload, setPublishPayload] = useState('');
  const [publishHeaders, setPublishHeaders] = useState('correlationId=evt-1\ncontent-type=application/json');
  const [flushProducer, setFlushProducer] = useState(true);

  const [newTopicName, setNewTopicName] = useState('');
  const [newPartitions, setNewPartitions] = useState('3');
  const [newReplicas, setNewReplicas] = useState('1');
  const [topicConfigs, setTopicConfigs] = useState('retention.ms=604800000');

  const [aclFilterType, setAclFilterType] = useState('TOPIC');
  const [aclFilterName, setAclFilterName] = useState('');
  const [aclForm, setAclForm] = useState<KafkaAclEntry>(emptyAcl());
  const [aclEditOld, setAclEditOld] = useState<KafkaAclEntry | null>(null);

  const { data: acls, refetch: refetchAcls } = useQuery({
    queryKey: ['kafka-acls', connectionId, aclFilterType, aclFilterName],
    queryFn: () =>
      api.kafkaListAcls(connectionId, {
        resourceType: aclFilterType || undefined,
        resourceName: aclFilterName || undefined,
      }),
    enabled: canAdmin,
  });

  const publishMutation = useMutation({
    mutationFn: () => {
      const headers = parseHeaderLines(publishHeaders);
      if (publishKey) headers.key = publishKey;
      if (publishPartition) headers.partition = publishPartition;
      return api.kafkaPublish(connectionId, {
        destination: topic,
        payload: publishPayload,
        headers,
        flush: flushProducer,
      });
    },
  });

  const createTopicMutation = useMutation({
    mutationFn: () => {
      const configs: Record<string, string> = {};
      for (const line of topicConfigs.split('\n')) {
        const t = line.trim();
        if (!t.includes('=')) continue;
        const i = t.indexOf('=');
        configs[t.slice(0, i).trim()] = t.slice(i + 1).trim();
      }
      return api.kafkaCreateTopic(connectionId, {
        name: newTopicName,
        partitions: parseInt(newPartitions, 10) || 1,
        replicationFactor: parseInt(newReplicas, 10) || 1,
        configs,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inspect-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['destinations', connectionId] });
    },
  });

  const deleteTopicMutation = useMutation({
    mutationFn: () => api.kafkaDeleteTopic(connectionId, topic),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inspect-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['destinations', connectionId] });
    },
  });

  const flushTopicMutation = useMutation({
    mutationFn: () => api.kafkaFlushTopic(connectionId, topic, {}),
  });

  const createAclMutation = useMutation({
    mutationFn: () => api.kafkaCreateAcl(connectionId, aclForm),
    onSuccess: () => refetchAcls(),
  });

  const deleteAclMutation = useMutation({
    mutationFn: (entry: KafkaAclEntry) => api.kafkaDeleteAcl(connectionId, entry),
    onSuccess: () => refetchAcls(),
  });

  const replaceAclMutation = useMutation({
    mutationFn: () => {
      if (!aclEditOld) throw new Error('Select ACL to edit');
      return api.kafkaReplaceAcl(connectionId, { oldBinding: aclEditOld, newBinding: aclForm });
    },
    onSuccess: () => {
      setAclEditOld(null);
      refetchAcls();
    },
  });

  if (!canPublish && !canAdmin) {
    return (
      <p className="inspector-meta">
        Kafka admin operations require ADMIN deployment mode (ADMIN_BROKER_OPS / PUBLISH).
      </p>
    );
  }

  return (
    <div className="kafka-admin">
      {canPublish && (
        <div className="card">
          <h3>Publish message</h3>
          <div className="form-grid">
            <div className="form-row">
              <label>Topic</label>
              <input value={topic} onChange={(e) => setTopic(e.target.value)} />
            </div>
            <div className="form-row">
              <label>Message key</label>
              <input value={publishKey} onChange={(e) => setPublishKey(e.target.value)} placeholder="optional" />
            </div>
            <div className="form-row">
              <label>Partition</label>
              <input
                value={publishPartition}
                onChange={(e) => setPublishPartition(e.target.value)}
                placeholder="optional"
              />
            </div>
          </div>
          <div className="form-row">
            <label>Payload</label>
            <textarea rows={4} value={publishPayload} onChange={(e) => setPublishPayload(e.target.value)} />
          </div>
          <div className="form-row">
            <label>Record headers (key=value per line)</label>
            <textarea rows={3} value={publishHeaders} onChange={(e) => setPublishHeaders(e.target.value)} />
          </div>
          <label className="inspector-meta">
            <input
              type="checkbox"
              checked={flushProducer}
              onChange={(e) => setFlushProducer(e.target.checked)}
            />{' '}
            Flush producer after send
          </label>
          <button
            type="button"
            disabled={publishMutation.isPending || !topic}
            onClick={() => publishMutation.mutate()}
          >
            Publish
          </button>
          {publishMutation.isError && (
            <p className="stream-error">{String(publishMutation.error)}</p>
          )}
          {publishMutation.data && (
            <pre className="inspector-pre">{JSON.stringify(publishMutation.data, null, 2)}</pre>
          )}
        </div>
      )}

      {canAdmin && (
        <>
          <div className="card">
            <h3>Topics</h3>
            <div className="form-row">
              <label>Selected topic</label>
              <input value={topic} onChange={(e) => setTopic(e.target.value)} />
            </div>
            <div className="inspector-actions">
              <button
                type="button"
                className="secondary"
                disabled={flushTopicMutation.isPending || !topic}
                onClick={() => flushTopicMutation.mutate()}
              >
                Flush topic (delete all records)
              </button>
              <button
                type="button"
                className="secondary"
                disabled={deleteTopicMutation.isPending || !topic}
                onClick={() => {
                  if (window.confirm(`Delete topic "${topic}"?`)) deleteTopicMutation.mutate();
                }}
              >
                Delete topic
              </button>
            </div>
            {(flushTopicMutation.data || deleteTopicMutation.data) && (
              <pre className="inspector-pre">
                {JSON.stringify(flushTopicMutation.data ?? deleteTopicMutation.data, null, 2)}
              </pre>
            )}
            <hr />
            <h4>Create topic</h4>
            <div className="form-grid">
              <div className="form-row">
                <label>Name</label>
                <input value={newTopicName} onChange={(e) => setNewTopicName(e.target.value)} />
              </div>
              <div className="form-row">
                <label>Partitions</label>
                <input value={newPartitions} onChange={(e) => setNewPartitions(e.target.value)} />
              </div>
              <div className="form-row">
                <label>Replication factor</label>
                <input value={newReplicas} onChange={(e) => setNewReplicas(e.target.value)} />
              </div>
            </div>
            <div className="form-row">
              <label>Configs (key=value per line)</label>
              <textarea rows={2} value={topicConfigs} onChange={(e) => setTopicConfigs(e.target.value)} />
            </div>
            <button
              type="button"
              disabled={createTopicMutation.isPending || !newTopicName}
              onClick={() => createTopicMutation.mutate()}
            >
              Create topic
            </button>
          </div>

          <div className="card">
            <h3>ACLs</h3>
            <div className="form-grid">
              <div className="form-row">
                <label>Filter resource type</label>
                <select value={aclFilterType} onChange={(e) => setAclFilterType(e.target.value)}>
                  <option value="">Any</option>
                  <option value="TOPIC">TOPIC</option>
                  <option value="GROUP">GROUP</option>
                  <option value="CLUSTER">CLUSTER</option>
                  <option value="TRANSACTIONAL_ID">TRANSACTIONAL_ID</option>
                </select>
              </div>
              <div className="form-row">
                <label>Filter resource name</label>
                <input value={aclFilterName} onChange={(e) => setAclFilterName(e.target.value)} />
              </div>
            </div>
            <button type="button" className="secondary" onClick={() => refetchAcls()}>
              Refresh ACLs
            </button>
            <table>
              <thead>
                <tr>
                  <th>Resource</th>
                  <th>Principal</th>
                  <th>Op</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {acls?.map((a, i) => (
                  <tr key={`${a.resourceName}-${a.principal}-${i}`}>
                    <td>
                      {a.resourceType}:{a.resourceName}
                    </td>
                    <td>{a.principal}</td>
                    <td>
                      {a.operation} ({a.permissionType})
                    </td>
                    <td>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setAclEditOld(a);
                          setAclForm({ ...a });
                        }}
                      >
                        Edit
                      </button>{' '}
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => deleteAclMutation.mutate(a)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <h4>{aclEditOld ? 'Edit ACL (replace)' : 'Add ACL'}</h4>
            <div className="form-grid">
              <div className="form-row">
                <label>Resource type</label>
                <input
                  value={aclForm.resourceType}
                  onChange={(e) => setAclForm({ ...aclForm, resourceType: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Resource name</label>
                <input
                  value={aclForm.resourceName}
                  onChange={(e) => setAclForm({ ...aclForm, resourceName: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Pattern</label>
                <select
                  value={aclForm.patternType ?? 'LITERAL'}
                  onChange={(e) => setAclForm({ ...aclForm, patternType: e.target.value })}
                >
                  <option value="LITERAL">LITERAL</option>
                  <option value="PREFIXED">PREFIXED</option>
                </select>
              </div>
              <div className="form-row">
                <label>Principal</label>
                <input
                  value={aclForm.principal}
                  onChange={(e) => setAclForm({ ...aclForm, principal: e.target.value })}
                  placeholder="User:alice"
                />
              </div>
              <div className="form-row">
                <label>Host</label>
                <input value={aclForm.host ?? '*'} onChange={(e) => setAclForm({ ...aclForm, host: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Operation</label>
                <input
                  value={aclForm.operation}
                  onChange={(e) => setAclForm({ ...aclForm, operation: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Permission</label>
                <select
                  value={aclForm.permissionType ?? 'ALLOW'}
                  onChange={(e) => setAclForm({ ...aclForm, permissionType: e.target.value })}
                >
                  <option value="ALLOW">ALLOW</option>
                  <option value="DENY">DENY</option>
                </select>
              </div>
            </div>
            {aclEditOld ? (
              <button
                type="button"
                disabled={replaceAclMutation.isPending}
                onClick={() => replaceAclMutation.mutate()}
              >
                Save (replace ACL)
              </button>
            ) : (
              <button type="button" disabled={createAclMutation.isPending} onClick={() => createAclMutation.mutate()}>
                Add ACL
              </button>
            )}
            {aclEditOld && (
              <button type="button" className="secondary" onClick={() => setAclEditOld(null)}>
                Cancel edit
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

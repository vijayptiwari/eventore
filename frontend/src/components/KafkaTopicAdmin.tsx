import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';

function parsePositiveInt(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const n = Number(trimmed);
  return n >= 1 ? n : null;
}

interface Props {
  connectionId: string;
  topic: string;
  onTopicChange: (topic: string) => void;
}

export default function KafkaTopicAdmin({ connectionId, topic, onTopicChange }: Props) {
  const qc = useQueryClient();
  const [newTopicName, setNewTopicName] = useState('');
  const [newPartitions, setNewPartitions] = useState('3');
  const [newReplicas, setNewReplicas] = useState('1');
  const [topicConfigs, setTopicConfigs] = useState('retention.ms=604800000');

  const partitions = parsePositiveInt(newPartitions);
  const replicas = parsePositiveInt(newReplicas);
  const partitionsInvalid = newPartitions.trim() !== '' && partitions === null;
  const replicasInvalid = newReplicas.trim() !== '' && replicas === null;
  const createTopicDisabled =
    !newTopicName || partitions === null || replicas === null;

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
        partitions: partitions ?? 1,
        replicationFactor: replicas ?? 1,
        configs,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inspect-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['live-view-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['destinations', connectionId] });
    },
  });

  const deleteTopicMutation = useMutation({
    mutationFn: () => api.kafkaDeleteTopic(connectionId, topic),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inspect-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['live-view-topics', connectionId] });
      qc.invalidateQueries({ queryKey: ['destinations', connectionId] });
    },
  });

  const flushTopicMutation = useMutation({
    mutationFn: () => api.kafkaFlushTopic(connectionId, topic, {}),
  });

  return (
    <div className="card">
      <h3>Topics</h3>
      <div className="form-row">
        <label>Selected topic</label>
        <input value={topic} onChange={(e) => onTopicChange(e.target.value)} />
      </div>
      <div className="inspector-actions">
        <button
          type="button"
          className="secondary"
          disabled={flushTopicMutation.isPending || !topic}
          onClick={() => {
            if (window.confirm(`Flush topic "${topic}" (delete all records)?`)) {
              flushTopicMutation.mutate();
            }
          }}
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
          {partitionsInvalid && (
            <p className="stream-error">Partitions must be a positive whole number.</p>
          )}
        </div>
        <div className="form-row">
          <label>Replication factor</label>
          <input value={newReplicas} onChange={(e) => setNewReplicas(e.target.value)} />
          {replicasInvalid && (
            <p className="stream-error">Replication factor must be a positive whole number.</p>
          )}
        </div>
      </div>
      <div className="form-row">
        <label>Configs (key=value per line)</label>
        <textarea rows={2} value={topicConfigs} onChange={(e) => setTopicConfigs(e.target.value)} />
      </div>
      <button
        type="button"
        disabled={createTopicMutation.isPending || createTopicDisabled}
        onClick={() => createTopicMutation.mutate()}
      >
        Create topic
      </button>
    </div>
  );
}

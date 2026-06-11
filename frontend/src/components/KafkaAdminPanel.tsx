import { useState } from 'react';
import { canAction } from '../api/client';
import { useAppConfig } from '../hooks/useAppConfig';
import KafkaAclAdmin from './KafkaAclAdmin';
import KafkaPublishForm from './KafkaPublishForm';
import KafkaTopicAdmin from './KafkaTopicAdmin';

interface Props {
  connectionId: string;
  defaultTopic: string;
}

export default function KafkaAdminPanel({ connectionId, defaultTopic }: Props) {
  const { data: config } = useAppConfig();
  const canPublish = canAction(config?.allowedActions, 'PUBLISH');
  const canAdmin = canAction(config?.allowedActions, 'ADMIN_BROKER_OPS');
  const [topic, setTopic] = useState(defaultTopic);

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
        <KafkaPublishForm connectionId={connectionId} topic={topic} onTopicChange={setTopic} />
      )}
      {canAdmin && (
        <>
          <KafkaTopicAdmin connectionId={connectionId} topic={topic} onTopicChange={setTopic} />
          <KafkaAclAdmin connectionId={connectionId} />
        </>
      )}
    </div>
  );
}

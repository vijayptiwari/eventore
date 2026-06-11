import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api, canAction, PROTOCOL_DEFAULTS } from '../api/client';
import { useAppConfig } from '../hooks/useAppConfig';
import { useControlPlane } from '../hooks/useControlPlane';
import type { StreamPlatformPreset } from '../api/platformTypes';
import type { ConnectionProfile, ProtocolType } from '../api/types';

const defaultProtocol = (protocols: ProtocolType[]): ProtocolType =>
  protocols[0] ?? 'KAFKA';

const emptyForm = (protocol: ProtocolType): ConnectionProfile => ({
  name: '',
  protocol,
  cloudProvider: 'ON_PREM',
  streamPlatform: 'GENERIC',
  brokerUrl: PROTOCOL_DEFAULTS[protocol].brokerUrl,
  properties: {},
  credentials: {},
});

interface ProtocolFieldDescriptor {
  kind: 'property' | 'credential';
  key: string;
  label: string;
  defaultValue?: string;
  password?: boolean;
  /** The KINESIS region doubles as the broker URL. */
  syncBrokerUrl?: boolean;
}

const PROTOCOL_EXTRA_FIELDS: Partial<Record<ProtocolType, ProtocolFieldDescriptor[]>> = {
  MQTT: [{ kind: 'property', key: 'topicFilter', label: 'Topic filter', defaultValue: '#' }],
  RABBITMQ: [
    { kind: 'property', key: 'vhost', label: 'Virtual host', defaultValue: '/' },
    { kind: 'property', key: 'queue', label: 'Default queue', defaultValue: 'eventore.queue' },
  ],
  GCP_PUBSUB: [
    {
      kind: 'property',
      key: 'subscription',
      label: 'Subscription name (for consume)',
      defaultValue: 'eventore-sub',
    },
  ],
  AZURE_SERVICE_BUS: [
    {
      kind: 'property',
      key: 'entityPath',
      label: 'Entity path (queue or topic)',
      defaultValue: 'eventore',
    },
    { kind: 'credential', key: 'connectionString', label: 'Connection string', password: true },
  ],
  KINESIS: [
    { kind: 'property', key: 'region', label: 'AWS region', syncBrokerUrl: true },
    { kind: 'credential', key: 'accessKeyId', label: 'Access key ID' },
    { kind: 'credential', key: 'secretAccessKey', label: 'Secret access key', password: true },
  ],
  JMS: [{ kind: 'property', key: 'queue', label: 'Default queue', defaultValue: 'eventore.queue' }],
};

export default function ConnectionsPage() {
  const { data: config } = useAppConfig();
  const { connectionProtocols: controlProtocols } = useControlPlane();
  const supportedProtocols = useMemo(
    () =>
      controlProtocols.length > 0
        ? controlProtocols
        : (config?.supportedProtocols ?? []),
    [controlProtocols, config?.supportedProtocols],
  );
  const queryClient = useQueryClient();
  const { data: connections, isLoading } = useQuery({
    queryKey: ['connections'],
    queryFn: api.listConnections,
  });
  const { data: platforms } = useQuery({
    queryKey: ['platforms'],
    queryFn: api.listPlatforms,
  });
  const visiblePlatforms = useMemo(
    () =>
      platforms?.filter((p) => supportedProtocols.includes(p.protocol)) ?? [],
    [platforms, supportedProtocols],
  );
  const [form, setForm] = useState<ConnectionProfile>(() =>
    emptyForm(defaultProtocol(supportedProtocols)),
  );
  const [selectedPresetKey, setSelectedPresetKey] = useState('');
  const canManage = canAction(config?.allowedActions, 'MANAGE_CONNECTIONS');

  useEffect(() => {
    if (!supportedProtocols.length) return;
    setForm((f) => {
      if (supportedProtocols.includes(f.protocol)) return f;
      const protocol = defaultProtocol(supportedProtocols);
      return { ...emptyForm(protocol), name: f.name };
    });
  }, [supportedProtocols]);

  const createMutation = useMutation({
    mutationFn: api.createConnection,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connections'] });
      setForm(emptyForm(defaultProtocol(supportedProtocols)));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: api.deleteConnection,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connections'] }),
  });

  type RowValidationState =
    | { status: 'pending' }
    | { status: 'success'; result: string }
    | { status: 'error'; message: string };

  const [validationById, setValidationById] = useState<Record<string, RowValidationState>>({});

  const testConnection = (id: string) => {
    setValidationById((prev) => ({ ...prev, [id]: { status: 'pending' } }));
    api
      .validateConnection(id)
      .then((data) => {
        setValidationById((prev) => ({
          ...prev,
          [id]: { status: 'success', result: data?.status ?? 'OK' },
        }));
      })
      .catch((err) => {
        setValidationById((prev) => ({
          ...prev,
          [id]: { status: 'error', message: String(err) },
        }));
      });
  };

  const onProtocolChange = (protocol: ProtocolType) => {
    setSelectedPresetKey('');
    setForm((f) => ({
      ...f,
      protocol,
      brokerUrl: PROTOCOL_DEFAULTS[protocol].brokerUrl,
    }));
  };

  const applyPreset = (preset: StreamPlatformPreset) => {
    const key = `${preset.platform}-${preset.protocol}-${preset.label}`;
    setSelectedPresetKey(key);
    setForm((f) => ({
      ...f,
      protocol: preset.protocol,
      cloudProvider: preset.cloudProvider,
      streamPlatform: preset.platform,
      brokerUrl: preset.brokerUrlHint,
      properties: { ...preset.defaultProperties },
    }));
  };

  return (
    <div>
      <h1>Connections</h1>
      {canManage && (
        <div className="card">
          <h2>New connection</h2>
          <div className="form-grid">
            <div className="form-row">
              <label>Name</label>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>
            <div className="form-row">
              <label>Stream platform preset</label>
              <select
                value={selectedPresetKey}
                onChange={(e) => {
                  const preset = platforms?.find(
                    (p) => `${p.platform}-${p.protocol}-${p.label}` === e.target.value,
                  );
                  if (preset) applyPreset(preset);
                  else setSelectedPresetKey('');
                }}
              >
                <option value="">Custom / manual</option>
                {visiblePlatforms.map((p) => {
                  const key = `${p.platform}-${p.protocol}-${p.label}`;
                  return (
                    <option key={key} value={key}>
                      [{p.cloudProvider}] {p.label}
                    </option>
                  );
                })}
              </select>
              {selectedPresetKey && (
                <p className="inspector-meta" style={{ marginTop: '0.35rem' }}>
                  {visiblePlatforms.find((p) => `${p.platform}-${p.protocol}-${p.label}` === selectedPresetKey)
                    ?.description}
                </p>
              )}
            </div>
            <div className="form-row">
              <label>Protocol</label>
              <select
                value={form.protocol}
                onChange={(e) => onProtocolChange(e.target.value as ProtocolType)}
              >
                {supportedProtocols.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Broker URL</label>
              <input
                value={form.brokerUrl}
                onChange={(e) => setForm({ ...form, brokerUrl: e.target.value })}
                placeholder={PROTOCOL_DEFAULTS[form.protocol].hint}
              />
            </div>
            <div className="form-row">
              <label>Username</label>
              <input
                value={form.credentials?.username ?? ''}
                onChange={(e) =>
                  setForm({
                    ...form,
                    credentials: { ...form.credentials, username: e.target.value },
                  })
                }
              />
            </div>
            <div className="form-row">
              <label>Password</label>
              <input
                type="password"
                value={form.credentials?.password ?? ''}
                onChange={(e) =>
                  setForm({
                    ...form,
                    credentials: { ...form.credentials, password: e.target.value },
                  })
                }
              />
            </div>
            {(PROTOCOL_EXTRA_FIELDS[form.protocol] ?? []).map((field) => {
              const current =
                field.kind === 'property'
                  ? form.properties?.[field.key]
                  : form.credentials?.[field.key];
              const fallback = field.syncBrokerUrl ? form.brokerUrl : (field.defaultValue ?? '');
              return (
                <div className="form-row" key={`${form.protocol}-${field.key}`}>
                  <label>{field.label}</label>
                  <input
                    type={field.password ? 'password' : 'text'}
                    value={current ?? fallback}
                    onChange={(e) =>
                      setForm((f) => ({
                        ...f,
                        ...(field.kind === 'property'
                          ? { properties: { ...f.properties, [field.key]: e.target.value } }
                          : { credentials: { ...f.credentials, [field.key]: e.target.value } }),
                        ...(field.syncBrokerUrl ? { brokerUrl: e.target.value } : {}),
                      }))
                    }
                  />
                </div>
              );
            })}
          </div>
          <button
            disabled={!form.name || createMutation.isPending}
            onClick={() => createMutation.mutate(form)}
          >
            Save connection
          </button>
          {createMutation.isError && (
            <p className="stream-error">{String(createMutation.error)}</p>
          )}
        </div>
      )}
      <div className="card">
        <h2>Saved connections</h2>
        {isLoading && <p>Loading...</p>}
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Protocol</th>
              <th>Broker</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {connections?.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>
                  <span className="tag">{c.protocol}</span>
                </td>
                <td>{c.brokerUrl}</td>
                <td>
                  <button
                    className="secondary"
                    onClick={() => c.id && testConnection(c.id)}
                    disabled={c.id ? validationById[c.id]?.status === 'pending' : false}
                  >
                    Test
                  </button>{' '}
                  {canManage && (
                    <button
                      className="secondary"
                      onClick={() => {
                        if (c.id && window.confirm(`Delete connection "${c.name}"?`)) {
                          deleteMutation.mutate(c.id);
                        }
                      }}
                      disabled={deleteMutation.isPending}
                    >
                      Delete
                    </button>
                  )}
                  {c.id && validationById[c.id]?.status === 'pending' && (
                    <span className="inspector-meta"> Testing…</span>
                  )}
                  {c.id && validationById[c.id]?.status === 'success' && (
                    <span className="tag tag-ok"> {validationById[c.id].result}</span>
                  )}
                  {c.id && validationById[c.id]?.status === 'error' && (
                    <span className="stream-error"> {validationById[c.id].message}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!connections?.length && <p>No connections yet.</p>}
      </div>
    </div>
  );
}

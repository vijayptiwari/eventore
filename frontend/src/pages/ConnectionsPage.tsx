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

  const validateMutation = useMutation({
    mutationFn: api.validateConnection,
  });

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
            {form.protocol === 'MQTT' && (
              <div className="form-row">
                <label>Topic filter</label>
                <input
                  value={form.properties?.topicFilter ?? '#'}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      properties: { ...form.properties, topicFilter: e.target.value },
                    })
                  }
                />
              </div>
            )}
            {form.protocol === 'RABBITMQ' && (
              <>
                <div className="form-row">
                  <label>Virtual host</label>
                  <input
                    value={form.properties?.vhost ?? '/'}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        properties: { ...form.properties, vhost: e.target.value },
                      })
                    }
                  />
                </div>
                <div className="form-row">
                  <label>Default queue</label>
                  <input
                    value={form.properties?.queue ?? 'eventore.queue'}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        properties: { ...form.properties, queue: e.target.value },
                      })
                    }
                  />
                </div>
              </>
            )}
            {form.protocol === 'GCP_PUBSUB' && (
              <div className="form-row">
                <label>Subscription name (for consume)</label>
                <input
                  value={form.properties?.subscription ?? 'eventore-sub'}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      properties: { ...form.properties, subscription: e.target.value },
                    })
                  }
                />
              </div>
            )}
            {form.protocol === 'AZURE_SERVICE_BUS' && (
              <>
                <div className="form-row">
                  <label>Entity path (queue or topic)</label>
                  <input
                    value={form.properties?.entityPath ?? 'eventore'}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        properties: { ...form.properties, entityPath: e.target.value },
                      })
                    }
                  />
                </div>
                <div className="form-row">
                  <label>Connection string</label>
                  <input
                    type="password"
                    value={form.credentials?.connectionString ?? ''}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        credentials: { ...form.credentials, connectionString: e.target.value },
                      })
                    }
                  />
                </div>
              </>
            )}
            {form.protocol === 'KINESIS' && (
              <>
                <div className="form-row">
                  <label>AWS region</label>
                  <input
                    value={form.properties?.region ?? form.brokerUrl}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        properties: { ...form.properties, region: e.target.value },
                        brokerUrl: e.target.value,
                      })
                    }
                  />
                </div>
                <div className="form-row">
                  <label>Access key ID</label>
                  <input
                    value={form.credentials?.accessKeyId ?? ''}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        credentials: { ...form.credentials, accessKeyId: e.target.value },
                      })
                    }
                  />
                </div>
                <div className="form-row">
                  <label>Secret access key</label>
                  <input
                    type="password"
                    value={form.credentials?.secretAccessKey ?? ''}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        credentials: { ...form.credentials, secretAccessKey: e.target.value },
                      })
                    }
                  />
                </div>
              </>
            )}
            {form.protocol === 'JMS' && (
              <div className="form-row">
                <label>Default queue</label>
                <input
                  value={form.properties?.queue ?? 'eventore.queue'}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      properties: { ...form.properties, queue: e.target.value },
                    })
                  }
                />
              </div>
            )}
          </div>
          <button
            disabled={!form.name || createMutation.isPending}
            onClick={() => createMutation.mutate(form)}
          >
            Save connection
          </button>
          {createMutation.isError && (
            <p style={{ color: '#f87171' }}>{String(createMutation.error)}</p>
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
                    onClick={() => c.id && validateMutation.mutate(c.id)}
                    disabled={validateMutation.isPending}
                  >
                    Test
                  </button>{' '}
                  {canManage && (
                    <button
                      className="secondary"
                      onClick={() => c.id && deleteMutation.mutate(c.id)}
                      disabled={deleteMutation.isPending}
                    >
                      Delete
                    </button>
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

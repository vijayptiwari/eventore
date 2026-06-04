export interface RuntimeConfig {
  apiBaseUrl: string;
  wsUrl: string;
}

declare global {
  interface Window {
    __EVENTORE_CONFIG__?: Partial<RuntimeConfig>;
  }
}

export function getRuntimeConfig(): RuntimeConfig {
  const injected = window.__EVENTORE_CONFIG__ ?? {};
  const origin = window.location.origin;
  const wsOrigin = origin.replace(/^http/, 'ws');
  return {
    apiBaseUrl: injected.apiBaseUrl ?? '/api/v1',
    wsUrl: injected.wsUrl ?? `${wsOrigin}/ws/stream`,
  };
}

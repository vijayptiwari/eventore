const API_TOKEN_STORAGE_KEY = 'eventore.apiToken';

export interface RuntimeConfig {
  apiBaseUrl: string;
  wsUrl: string;
  apiToken?: string;
}

declare global {
  interface Window {
    __EVENTORE_CONFIG__?: Partial<RuntimeConfig>;
  }
}

function resolveApiToken(injected?: string): string | undefined {
  if (injected?.trim()) {
    return injected.trim();
  }
  try {
    const stored = sessionStorage.getItem(API_TOKEN_STORAGE_KEY);
    return stored?.trim() ? stored.trim() : undefined;
  } catch {
    return undefined;
  }
}

export function getRuntimeConfig(): RuntimeConfig {
  const injected = window.__EVENTORE_CONFIG__ ?? {};
  const origin = window.location.origin;
  const wsOrigin = origin.replace(/^http/, 'ws');
  const apiToken = resolveApiToken(injected.apiToken);
  return {
    apiBaseUrl: injected.apiBaseUrl ?? '/api/v1',
    wsUrl: injected.wsUrl ?? `${wsOrigin}/ws/stream`,
    ...(apiToken ? { apiToken } : {}),
  };
}
